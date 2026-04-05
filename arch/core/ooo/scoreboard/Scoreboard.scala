package arch.core.ooo

import arch.configs._
import arch.core.ooo.{ MicroOp, Scheduler }
import chisel3._
import chisel3.util._

class Scoreboard(implicit p: Parameters) extends Scheduler {
  override def desiredName: String = s"${p(ISA).name}_superscalar_scoreboard"

  private val NUM_REGS = p(NumArchRegs)
  private val NO_PROD  = p(FunctionalUnits).size.U(log2Ceil(p(FunctionalUnits).size + 1).W)

  val reg_prod = RegInit(VecInit(Seq.fill(NUM_REGS)(NO_PROD)))
  val fu_busy  = RegInit(VecInit(Seq.fill(p(FunctionalUnits).size)(false.B)))

  val lane_can_issue = Wire(Vec(p(IssueWidth), Bool()))

  when(flush) {
    fu_busy.foreach(_ := false.B)
    reg_prod.foreach(_ := NO_PROD)
  }.otherwise {
    // Completion Routing
    for (i <- 0 until p(FunctionalUnits).size) {
      val done = fu_done(i)
      when(done.valid) {
        fu_busy(i) := false.B
        when(done.bits.rd =/= 0.U && reg_prod(done.bits.rd) === i.U) {
          reg_prod(done.bits.rd) := NO_PROD
        }
      }
    }

    // Issue Logic State Updates
    for (w <- 0 until p(IssueWidth)) {
      val req = dis_reqs(w).bits
      when(lane_can_issue(w)) {
        fu_busy(req.fu_id) := true.B
        when(req.rd =/= 0.U) {
          reg_prod(req.rd) := req.fu_id
        }
      }
    }
  }

  // Issue Evaluation
  for (w <- 0 until p(IssueWidth)) {
    val req        = dis_reqs(w).bits
    val valid      = dis_reqs(w).valid
    val is_zero_rd = req.rd === 0.U

    val struct_hazard  = fu_busy(req.fu_id)
    val waw_hazard     = (reg_prod(req.rd) =/= NO_PROD) && !is_zero_rd
    val raw_hazard_rs1 = (reg_prod(req.rs1) =/= NO_PROD) && (req.rs1 =/= 0.U)
    val raw_hazard_rs2 = (reg_prod(req.rs2) =/= NO_PROD) && (req.rs2 =/= 0.U)

    val has_hazard = struct_hazard || waw_hazard || raw_hazard_rs1 || raw_hazard_rs2
    val fu_ready   = fu_reqs(req.fu_id).ready

    val is_ready = !has_hazard && fu_ready

    dis_reqs(w).ready := is_ready
    lane_can_issue(w) := valid && is_ready && !flush
  }

  // Routing requests to FUs
  for (i <- 0 until p(FunctionalUnits).size) {
    fu_reqs(i).valid := false.B
    fu_reqs(i).bits  := 0.U.asTypeOf(new MicroOp) // Default empty

    for (w <- 0 until p(IssueWidth))
      when(lane_can_issue(w) && dis_reqs(w).bits.fu_id === i.U) {
        fu_reqs(i).valid := true.B
        fu_reqs(i).bits  := dis_reqs(w).bits
      }
  }
}
