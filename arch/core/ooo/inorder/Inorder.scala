package arch.core.ooo

import arch.core.regfile._
import arch.configs._
import chisel3._
import chisel3.util.{ PriorityEncoder, Mux1H }

class Inorder(implicit p: Parameters) extends Scheduler {
  override def desiredName: String = s"${p(ISA).name}_in_order_scheduler"

  val regfile_utils = RegfileUtilsFactory.getOrThrow(p(ISA).name)

  val reg_pending         = RegInit(VecInit(Seq.fill(numRegs)(false.B)))
  val reg_completed_valid = RegInit(VecInit(Seq.fill(numRegs)(false.B)))
  val reg_completed_data  = RegInit(VecInit(Seq.fill(numRegs)(0.U(p(XLen).W))))

  defaultFuReqs()
  defaultDispatchReady()

  val cdb_hit   = Wire(Vec(numRegs, Vec(numFUs, Bool())))
  val cdb_valid = Wire(Vec(numRegs, Bool()))
  val cdb_data  = Wire(Vec(numRegs, UInt(p(XLen).W)))

  for (r <- 0 until numRegs) {
    for (f <- 0 until numFUs)
      cdb_hit(r)(f) := fu_done(f).valid && fu_done(f).bits.rd === r.U && regfile_utils.writable(r.U)

    cdb_valid(r) := cdb_hit(r).asUInt.orR
    cdb_data(r)  := Mux1H(cdb_hit(r), fu_done.map(_.bits.result))
  }

  val temp_pending         = Wire(Vec(p(IssueWidth) + 1, Vec(numRegs, Bool())))
  val temp_completed_valid = Wire(Vec(p(IssueWidth) + 1, Vec(numRegs, Bool())))
  val temp_completed_data  = Wire(Vec(p(IssueWidth) + 1, Vec(numRegs, UInt(p(XLen).W))))
  val temp_fu_used         = Wire(Vec(p(IssueWidth) + 1, Vec(numFUs, Bool())))
  val accepted             = Wire(Vec(p(IssueWidth), Bool()))

  for (r <- 0 until numRegs) {
    temp_pending(0)(r)         := reg_pending(r) && !cdb_valid(r)
    temp_completed_valid(0)(r) := Mux(cdb_valid(r), true.B, reg_completed_valid(r))
    temp_completed_data(0)(r)  := Mux(cdb_valid(r), cdb_data(r), reg_completed_data(r))
  }

  for (f <- 0 until numFUs)
    temp_fu_used(0)(f) := false.B

  for (w <- 0 until p(IssueWidth)) {
    val dis = dis_reqs(w)
    val op  = dis.bits

    val rs1_used = op.rs1_valid && regfile_utils.readable(op.rs1)
    val rs2_used = op.rs2_valid && regfile_utils.readable(op.rs2)
    val rd_used  = op.rd_valid && regfile_utils.writable(op.rd)

    val rs1_haz = rs1_used && temp_pending(w)(op.rs1)
    val rs2_haz = rs2_used && temp_pending(w)(op.rs2)
    val waw_haz = rd_used && temp_pending(w)(op.rd)

    val rs1_from_completed = rs1_used && temp_completed_valid(w)(op.rs1)
    val rs2_from_completed = rs2_used && temp_completed_valid(w)(op.rs2)

    val rs1_value = Mux(rs1_from_completed, temp_completed_data(w)(op.rs1), op.rs1_data)
    val rs2_value = Mux(rs2_from_completed, temp_completed_data(w)(op.rs2), op.rs2_data)

    val fu_match = Wire(Vec(numFUs, Bool()))

    for (f <- 0 until numFUs)
      fu_match(f) := !temp_fu_used(w)(f) && fu_reqs(f).ready && fuTypes(f) === op.fu_type

    val target    = PriorityEncoder(fu_match)
    val fu_ok     = fu_match.asUInt.orR
    val prev_ok   = if (w == 0) true.B else !dis_reqs(w - 1).valid || accepted(w - 1)
    val can_issue = prev_ok && fu_ok && !rs1_haz && !rs2_haz && !waw_haz

    dis.ready   := can_issue
    accepted(w) := dis.valid && can_issue

    temp_pending(w + 1)         := temp_pending(w)
    temp_completed_valid(w + 1) := temp_completed_valid(w)
    temp_completed_data(w + 1)  := temp_completed_data(w)
    temp_fu_used(w + 1)         := temp_fu_used(w)

    when(accepted(w)) {
      val issueOp = Wire(new MicroOp)
      issueOp          := op
      issueOp.fu_id    := target
      issueOp.rs1_data := rs1_value
      issueOp.rs2_data := rs2_value

      for (f <- 0 until numFUs)
        when(target === f.U) {
          fu_reqs(f).valid := true.B
          fu_reqs(f).bits  := issueOp
        }

      temp_fu_used(w + 1)(target) := true.B

      when(rd_used) {
        temp_pending(w + 1)(op.rd)         := true.B
        temp_completed_valid(w + 1)(op.rd) := false.B
        temp_completed_data(w + 1)(op.rd)  := 0.U
      }
    }
  }

  when(flush) {
    reg_pending.foreach(_ := false.B)
    reg_completed_valid.foreach(_ := false.B)
    reg_completed_data.foreach(_ := 0.U)
  }.otherwise {
    reg_pending         := temp_pending(p(IssueWidth))
    reg_completed_valid := temp_completed_valid(p(IssueWidth))
    reg_completed_data  := temp_completed_data(p(IssueWidth))
  }
}
