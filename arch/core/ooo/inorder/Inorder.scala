package arch.core.ooo

import arch.core.regfile._
import arch.configs._
import chisel3._
import chisel3.util.PriorityEncoder

class Inorder(implicit p: Parameters) extends Scheduler {
  override def desiredName: String = s"${p(ISA).name}_in_order_scheduler"

  val regfile_utils = RegfileUtilsFactory.getOrThrow(p(ISA).name)

  val reg_pending = RegInit(VecInit(Seq.fill(numRegs)(false.B)))

  defaultFuReqs()
  defaultDispatchReady()

  val clear = Wire(Vec(numRegs, Bool()))
  for (r <- 0 until numRegs) {
    clear(r) := false.B

    for (f <- 0 until numFUs)
      when(
        fu_done(f).valid &&
          fu_done(f).bits.rd === r.U &&
          regfile_utils.writable(r.U)
      ) {
        clear(r) := true.B
      }
  }

  val temp_pending = Wire(Vec(p(IssueWidth) + 1, Vec(numRegs, Bool())))
  val temp_fu_used = Wire(Vec(p(IssueWidth) + 1, Vec(numFUs, Bool())))

  for (r <- 0 until numRegs)
    temp_pending(0)(r) := reg_pending(r) && !clear(r)

  for (f <- 0 until numFUs)
    temp_fu_used(0)(f) := false.B

  for (w <- 0 until p(IssueWidth)) {
    val dis = dis_reqs(w)
    val op  = dis.bits

    val rs1_haz =
      usesRs1(op) &&
        regfile_utils.readable(op.rs1) &&
        temp_pending(w)(op.rs1)

    val rs2_haz =
      usesRs2(op) &&
        regfile_utils.readable(op.rs2) &&
        temp_pending(w)(op.rs2)

    val waw_haz =
      regfile_utils.writable(op.rd) &&
        temp_pending(w)(op.rd)

    val fu_match = Wire(Vec(numFUs, Bool()))
    for (f <- 0 until numFUs)
      fu_match(f) :=
        !temp_fu_used(w)(f) &&
          fu_reqs(f).ready &&
          fuTypes(f) === op.fu_type

    val target = PriorityEncoder(fu_match)
    val fu_ok  = fu_match.asUInt.orR

    val prev_ok =
      if (w == 0) true.B else dis_reqs(w - 1).ready

    val can_issue =
      prev_ok &&
        fu_ok &&
        !rs1_haz &&
        !rs2_haz &&
        !waw_haz

    dis.ready := can_issue

    temp_pending(w + 1) := temp_pending(w)
    temp_fu_used(w + 1) := temp_fu_used(w)

    when(dis.valid && can_issue) {
      val issueOp = Wire(new MicroOp)
      issueOp       := op
      issueOp.fu_id := target

      fu_reqs(target).valid := true.B
      fu_reqs(target).bits  := issueOp

      temp_fu_used(w + 1)(target) := true.B

      when(regfile_utils.writable(op.rd)) {
        temp_pending(w + 1)(op.rd) := true.B
      }
    }
  }

  when(flush) {
    reg_pending.foreach(_ := false.B)
  }.otherwise {
    reg_pending := temp_pending(p(IssueWidth))
  }
}
