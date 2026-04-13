package arch.core.ooo

import arch.core.regfile._
import arch.configs._
import chisel3._
import chisel3.util.{ PriorityEncoder, PopCount }

class Inorder(implicit p: Parameters) extends Scheduler {
  override def desiredName: String = s"${p(ISA).name}_in_order"

  val regfile_utils = RegfileUtilitiesFactory.getOrThrow(p(ISA).name)

  val numRegs = p(NumArchRegs)
  val numFUs  = p(FunctionalUnits).size

  // Register Scoreboard
  val reg_pending = RegInit(VecInit(Seq.fill(numRegs)(false.B)))

  val clear_masks = Wire(Vec(numFUs, Vec(numRegs, Bool())))
  for (i <- 0 until numFUs)
    for (r <- 0 until numRegs)
      clear_masks(i)(r) := fu_done(i).valid && (fu_done(i).bits.rd === r.U) && regfile_utils.writable(r.U)

  val TYPE_LSU = arch.configs.proto.FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LSU.index.U
  val fu_types = p(FunctionalUnits).map(_.`type`.value.U(8.W))

  val temp_reg_pending = Wire(Vec(p(IssueWidth) + 1, Vec(numRegs, Bool())))
  temp_reg_pending(0) := reg_pending

  val temp_fu_avail = Wire(Vec(p(IssueWidth) + 1, Vec(numFUs, Bool())))
  for (i <- 0 until numFUs) temp_fu_avail(0)(i) := fu_reqs(i).ready

  // Memory Dependency State
  val inflight_stores = RegInit(0.U(8.W))
  val inflight_loads  = RegInit(0.U(8.W))

  val temp_inflight_stores = Wire(Vec(p(IssueWidth) + 1, UInt(8.W)))
  val temp_inflight_loads  = Wire(Vec(p(IssueWidth) + 1, UInt(8.W)))

  // Calculate completions from CDB
  val store_done_count = PopCount((0 until numFUs).map(f => fu_done(f).valid && fu_types(f) === TYPE_LSU && !regfile_utils.writable(fu_done(f).bits.rd)))
  val load_done_count  = PopCount((0 until numFUs).map(f => fu_done(f).valid && fu_types(f) === TYPE_LSU && regfile_utils.writable(fu_done(f).bits.rd)))

  temp_inflight_stores(0) := inflight_stores - store_done_count
  temp_inflight_loads(0)  := inflight_loads - load_done_count

  // Default all FU requests to invalid
  fu_reqs.foreach { req =>
    req.valid := false.B
    req.bits  := 0.U.asTypeOf(new MicroOp)
  }

  // Dispatch Logic
  for (w <- 0 until p(IssueWidth)) {
    val dis_req = dis_reqs(w)
    val op      = dis_req.bits

    // Data hazard checks against the current cycle state
    val rs1_hazard  = temp_reg_pending(w)(op.rs1) && regfile_utils.readable(op.rs1)
    val rs2_hazard  = temp_reg_pending(w)(op.rs2) && regfile_utils.readable(op.rs2)
    val rd_hazard   = temp_reg_pending(w)(op.rd) && regfile_utils.writable(op.rd)
    val data_hazard = rs1_hazard || rs2_hazard || rd_hazard

    val req_fu_type = WireDefault(0.U(8.W))
    for (i <- 0 until numFUs)
      when(op.fu_id === i.U)(req_fu_type := fu_types(i))

    val is_lsu   = req_fu_type === TYPE_LSU
    val is_store = is_lsu && !regfile_utils.writable(op.rd)
    val is_load  = is_lsu && regfile_utils.writable(op.rd)

    // Memory Hazard check
    val mem_hazard = Mux(is_store, (temp_inflight_loads(w) =/= 0.U) || (temp_inflight_stores(w) =/= 0.U), Mux(is_load, temp_inflight_stores(w) =/= 0.U, false.B))

    val fu_match_mask = Wire(Vec(numFUs, Bool()))
    for (i <- 0 until numFUs)
      fu_match_mask(i) := temp_fu_avail(w)(i) && (fu_types(i) === req_fu_type)

    val target_fu_idx = PriorityEncoder(fu_match_mask)
    val fu_available  = fu_match_mask.asUInt =/= 0.U

    val prev_issued = if (w == 0) true.B else dis_reqs(w - 1).ready

    val ready_to_issue = !data_hazard && !mem_hazard && fu_available && prev_issued
    dis_req.ready := ready_to_issue

    val can_issue = dis_req.valid && ready_to_issue

    // State progression for superscalar widths
    temp_reg_pending(w + 1)     := temp_reg_pending(w)
    temp_fu_avail(w + 1)        := temp_fu_avail(w)
    temp_inflight_stores(w + 1) := temp_inflight_stores(w)
    temp_inflight_loads(w + 1)  := temp_inflight_loads(w)

    when(can_issue) {
      fu_reqs(target_fu_idx).valid := true.B
      fu_reqs(target_fu_idx).bits  := op

      temp_fu_avail(w + 1)(target_fu_idx) := false.B
      when(regfile_utils.writable(op.rd)) {
        temp_reg_pending(w + 1)(op.rd) := true.B
      }
      when(is_store)(temp_inflight_stores(w + 1) := temp_inflight_stores(w) + 1.U)
      when(is_load)(temp_inflight_loads(w + 1)   := temp_inflight_loads(w) + 1.U)
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
    inflight_stores := 0.U
    inflight_loads  := 0.U
  }.otherwise {
    reg_pending     := next_reg_pending
    inflight_stores := temp_inflight_stores(p(IssueWidth))
    inflight_loads  := temp_inflight_loads(p(IssueWidth))
  }
}
