package arch.core.ooo

import arch.configs._
import chisel3._
import chisel3.util._

class Scoreboard(implicit p: Parameters) extends Module {
  override def desiredName: String = "scoreboard"

  val io = IO(new ScoreboardIO)

  private val NUM_FUS  = FURegistry.numFUs
  private val NUM_REGS = p(NumArchRegs)
  private val NO_PROD  = NUM_FUS.U(log2Ceil(NUM_FUS + 1).W)

  val fu_busy = RegInit(VecInit(Seq.fill(NUM_FUS)(false.B)))
  val fu_rd   = RegInit(VecInit(Seq.fill(NUM_FUS)(0.U(log2Ceil(NUM_REGS).W))))
  val fu_rs1  = RegInit(VecInit(Seq.fill(NUM_FUS)(0.U(log2Ceil(NUM_REGS).W))))
  val fu_rs2  = RegInit(VecInit(Seq.fill(NUM_FUS)(0.U(log2Ceil(NUM_REGS).W))))
  val fu_op   = RegInit(VecInit(Seq.fill(NUM_FUS)(0.U(p(ILen).W))))

  val reg_prod = RegInit(VecInit(Seq.fill(NUM_REGS)(NO_PROD)))

  val fu_freeing = Wire(Vec(NUM_FUS, Bool()))
  for (i <- 0 until NUM_FUS)
    fu_freeing(i) := io.fu_done(i) && fu_busy(i)

  val structural_hazard   = fu_busy(io.issue_fu_id)
  val structural_resolves = fu_freeing(io.issue_fu_id)

  val waw_hazard = (0 until NUM_FUS)
    .map { i =>
      fu_busy(i) && (fu_rd(i) === io.issue_rd) && (io.issue_rd =/= 0.U)
    }
    .reduce(_ || _)

  val waw_resolves = (0 until NUM_FUS)
    .map { i =>
      val is_conflict = fu_busy(i) && (fu_rd(i) === io.issue_rd) && (io.issue_rd =/= 0.U)
      !is_conflict || fu_freeing(i)
    }
    .reduce(_ && _)

  io.issue_ready := (!structural_hazard || structural_resolves) &&
    (!waw_hazard || waw_resolves)

  def operandReady(rs: UInt): Bool = {
    val prod             = reg_prod(rs)
    val no_producer      = prod === NO_PROD
    val producer_freeing = (0 until NUM_FUS)
      .map { i =>
        (prod === i.U) && fu_freeing(i)
      }
      .reduce(_ || _)
    no_producer || producer_freeing || (rs === 0.U)
  }

  io.rs1_ready := operandReady(io.issue_rs1)
  io.rs2_ready := operandReady(io.issue_rs2)

  for (i <- 0 until NUM_FUS)
    when(fu_freeing(i)) {
      fu_busy(i) := false.B
      when(fu_rd(i) =/= 0.U && reg_prod(fu_rd(i)) === i.U) {
        reg_prod(fu_rd(i)) := NO_PROD
      }
    }

  when(io.issue_valid && io.issue_ready) {
    val fu = io.issue_fu_id
    fu_busy(fu) := true.B
    fu_op(fu)   := io.issue_instr
    fu_rd(fu)   := io.issue_rd
    fu_rs1(fu)  := io.issue_rs1
    fu_rs2(fu)  := io.issue_rs2

    when(io.issue_rd =/= 0.U) {
      reg_prod(io.issue_rd) := fu
    }
  }

  for (i <- 0 until NUM_FUS) {
    io.fu_status(i).busy      := fu_busy(i)
    io.fu_status(i).op        := fu_op(i)
    io.fu_status(i).rd        := fu_rd(i)
    io.fu_status(i).rs1       := fu_rs1(i)
    io.fu_status(i).rs2       := fu_rs2(i)
    io.fu_status(i).rs1_prod  := reg_prod(fu_rs1(i))
    io.fu_status(i).rs2_prod  := reg_prod(fu_rs2(i))
    io.fu_status(i).rs1_ready := operandReady(fu_rs1(i))
    io.fu_status(i).rs2_ready := operandReady(fu_rs2(i))

    io.instr_status(i).issue        := fu_busy(i)
    io.instr_status(i).read_oper    := fu_busy(i)
    io.instr_status(i).exec_comp    := io.fu_done(i)
    io.instr_status(i).write_result := fu_freeing(i)
  }

  for (r <- 0 until NUM_REGS) {
    io.reg_status(r).rs   := r.U
    io.reg_status(r).prod := reg_prod(r)
  }
}
