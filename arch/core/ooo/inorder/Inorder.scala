package arch.core.ooo

import arch.configs._
import chisel3._
import chisel3.util.PriorityEncoder

class Inorder(implicit p: Parameters) extends Scheduler {
  override def desiredName: String = s"${p(ISA).name}_in_order"

  val numRegs = p(NumArchRegs)
  val numFUs  = p(FunctionalUnits).size

  // Register Scoreboard (Locking)
  val reg_pending = RegInit(VecInit(Seq.fill(numRegs)(false.B)))

  val clear_masks = Wire(Vec(numFUs, Vec(numRegs, Bool())))
  for (i <- 0 until numFUs)
    for (r <- 0 until numRegs)
      clear_masks(i)(r) := fu_done(i).valid && (fu_done(i).bits.rd === r.U) && (r.U =/= 0.U)

  val fu_types = p(FunctionalUnits).map(_.`type`.value.U(8.W))

  val temp_reg_pending = Wire(Vec(p(IssueWidth) + 1, Vec(numRegs, Bool())))
  temp_reg_pending(0) := reg_pending

  val temp_fu_avail = Wire(Vec(p(IssueWidth) + 1, Vec(numFUs, Bool())))
  for (i <- 0 until numFUs) temp_fu_avail(0)(i) := fu_reqs(i).ready

  // Default all FU requests to invalid
  fu_reqs.foreach { req =>
    req.valid := false.B
    req.bits  := 0.U.asTypeOf(new MicroOp)
  }

  // Dispatch Logic
  for (w <- 0 until p(IssueWidth)) {
    val dis_req = dis_reqs(w)
    val op      = dis_req.bits

    // Hazard checks against the current cycle state
    val rs1_hazard  = temp_reg_pending(w)(op.rs1) && (op.rs1 =/= 0.U)
    val rs2_hazard  = temp_reg_pending(w)(op.rs2) && (op.rs2 =/= 0.U)
    val rd_hazard   = temp_reg_pending(w)(op.rd) && (op.rd =/= 0.U)
    val data_hazard = rs1_hazard || rs2_hazard || rd_hazard

    val req_fu_type = WireDefault(0.U(8.W))
    for (i <- 0 until numFUs)
      when(op.fu_id === i.U)(req_fu_type := fu_types(i))

    // Dynamic Routing: Find ANY available FU that matches the required type
    val fu_match_mask = Wire(Vec(numFUs, Bool()))
    for (i <- 0 until numFUs)
      fu_match_mask(i) := temp_fu_avail(w)(i) && (fu_types(i) === req_fu_type)

    val target_fu_idx = PriorityEncoder(fu_match_mask)
    val fu_available  = fu_match_mask.asUInt =/= 0.U

    val prev_issued = if (w == 0) true.B else dis_reqs(w - 1).ready

    // Ready must NOT depend on valid to prevent FIRRTL combinational loops
    val ready_to_issue = !data_hazard && fu_available && prev_issued
    dis_req.ready := ready_to_issue

    val can_issue = dis_req.valid && ready_to_issue

    // State progression for superscalar widths
    temp_reg_pending(w + 1) := temp_reg_pending(w)
    temp_fu_avail(w + 1)    := temp_fu_avail(w)

    when(can_issue) {
      fu_reqs(target_fu_idx).valid := true.B
      fu_reqs(target_fu_idx).bits  := op

      temp_fu_avail(w + 1)(target_fu_idx) := false.B
      when(op.rd =/= 0.U) {
        temp_reg_pending(w + 1)(op.rd) := true.B
      }
    }
  }

  // State Update
  val next_reg_pending = Wire(Vec(numRegs, Bool()))
  for (r <- 0 until numRegs) {
    val cleared           = (0 until numFUs).map(i => clear_masks(i)(r)).foldLeft(false.B)(_ || _)
    val issued_this_cycle = temp_reg_pending(p(IssueWidth))(r) && !reg_pending(r)

    next_reg_pending(r) := Mux(issued_this_cycle, true.B, Mux(cleared, false.B, temp_reg_pending(p(IssueWidth))(r)))
  }

  when(flush) {
    reg_pending.foreach(_ := false.B)
  }.otherwise {
    reg_pending := next_reg_pending
  }
}
