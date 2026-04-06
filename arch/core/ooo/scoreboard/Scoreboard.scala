package arch.core.ooo

import arch.configs._
import arch.core.ooo.{ MicroOp, Scheduler }
import chisel3._
import chisel3.util.{ PriorityEncoder, log2Ceil, Mux1H }

class ScoreboardEntry(implicit p: Parameters) extends Bundle {
  val valid    = Bool()
  val op       = new MicroOp
  val q1_ready = Bool()
  val q1_tag   = UInt(log2Ceil(p(ROBSize)).W)
  val v1       = UInt(p(XLen).W)
  val q2_ready = Bool()
  val q2_tag   = UInt(log2Ceil(p(ROBSize)).W)
  val v2       = UInt(p(XLen).W)
}

class Scoreboard(implicit p: Parameters) extends Scheduler {
  override def desiredName: String = s"${p(ISA).name}_scoreboard"

  val numRegs = p(NumArchRegs)
  val numFUs  = p(FunctionalUnits).size

  val fu_types = p(FunctionalUnits).map(_.`type`.value.U(8.W))

  // Core State
  val reg_pending_valid = RegInit(VecInit(Seq.fill(numRegs)(false.B)))
  val reg_pending_rob   = RegInit(VecInit(Seq.fill(numRegs)(0.U(log2Ceil(p(ROBSize)).W))))

  // Issue Queues
  val entries      = RegInit(VecInit(Seq.fill(numFUs)(0.U.asTypeOf(new ScoreboardEntry))))
  val next_entries = Wire(Vec(numFUs, new ScoreboardEntry))
  next_entries := entries

  // CDB Unpacking
  val cdb_valid   = Wire(Vec(numFUs, Bool()))
  val cdb_data    = Wire(Vec(numFUs, UInt(p(XLen).W)))
  val cdb_rob_tag = Wire(Vec(numFUs, UInt(log2Ceil(p(ROBSize)).W)))
  val cdb_rd      = Wire(Vec(numFUs, UInt(log2Ceil(numRegs).W)))

  for (i <- 0 until numFUs) {
    cdb_valid(i)   := fu_done(i).valid
    cdb_data(i)    := fu_done(i).bits.result
    cdb_rob_tag(i) := fu_done(i).bits.rob_tag
    cdb_rd(i)      := fu_done(i).bits.rd
  }

  // Scoreboard Issue & CDB Snooping
  for (i <- 0 until numFUs) {
    when(entries(i).valid && !entries(i).q1_ready) {
      for (c <- 0 until numFUs)
        when(cdb_valid(c) && entries(i).q1_tag === cdb_rob_tag(c)) {
          next_entries(i).q1_ready := true.B
          next_entries(i).v1       := cdb_data(c)
        }
    }
    when(entries(i).valid && !entries(i).q2_ready) {
      for (c <- 0 until numFUs)
        when(cdb_valid(c) && entries(i).q2_tag === cdb_rob_tag(c)) {
          next_entries(i).q2_ready := true.B
          next_entries(i).v2       := cdb_data(c)
        }
    }

    val ready_to_exec = entries(i).valid && next_entries(i).q1_ready && next_entries(i).q2_ready

    fu_reqs(i).valid         := ready_to_exec
    fu_reqs(i).bits          := entries(i).op
    fu_reqs(i).bits.rs1_data := next_entries(i).v1
    fu_reqs(i).bits.rs2_data := next_entries(i).v2

    when(ready_to_exec && fu_reqs(i).ready) {
      next_entries(i).valid := false.B
    }
  }

  // Dispatch Logic
  val temp_reg_valid = Wire(Vec(p(IssueWidth) + 1, Vec(numRegs, Bool())))
  val temp_reg_rob   = Wire(Vec(p(IssueWidth) + 1, Vec(numRegs, UInt(log2Ceil(p(ROBSize)).W))))
  val temp_fu_avail  = Wire(Vec(p(IssueWidth) + 1, Vec(numFUs, Bool())))

  temp_reg_valid(0)                             := reg_pending_valid
  temp_reg_rob(0)                               := reg_pending_rob
  for (i <- 0 until numFUs) temp_fu_avail(0)(i) := !entries(i).valid

  for (w <- 0 until p(IssueWidth)) {
    val dis_req = dis_reqs(w)
    val op      = dis_req.bits

    val req_fu_type = WireDefault(0.U(8.W))
    for (i <- 0 until numFUs) when(op.fu_id === i.U)(req_fu_type := fu_types(i))

    val fu_match_mask = Wire(Vec(numFUs, Bool()))
    for (i <- 0 until numFUs) fu_match_mask(i) := temp_fu_avail(w)(i) && (fu_types(i) === req_fu_type)

    val target_fu_idx = PriorityEncoder(fu_match_mask)
    val fu_available  = fu_match_mask.asUInt =/= 0.U

    val waw_hazard = temp_reg_valid(w)(op.rd) && op.rd =/= 0.U

    val prev_issued = if (w == 0) true.B else dis_reqs(w - 1).ready

    val ready_to_issue = !waw_hazard && fu_available && prev_issued
    dis_req.ready := ready_to_issue

    val can_issue = dis_req.valid && ready_to_issue

    temp_reg_valid(w + 1) := temp_reg_valid(w)
    temp_reg_rob(w + 1)   := temp_reg_rob(w)
    temp_fu_avail(w + 1)  := temp_fu_avail(w)

    when(can_issue) {
      temp_fu_avail(w + 1)(target_fu_idx) := false.B
      when(op.rd =/= 0.U) {
        temp_reg_valid(w + 1)(op.rd) := true.B
        temp_reg_rob(w + 1)(op.rd)   := op.rob_tag
      }

      next_entries(target_fu_idx).valid := true.B
      next_entries(target_fu_idx).op    := op

      val r1_pending   = temp_reg_valid(w)(op.rs1)
      val r1_rob_tag   = temp_reg_rob(w)(op.rs1)
      val r1_cdb_valid = (0 until numFUs).map(c => cdb_valid(c) && r1_rob_tag === cdb_rob_tag(c)).foldLeft(false.B)(_ || _)
      val r1_cdb_data  = Mux1H((0 until numFUs).map(c => (cdb_valid(c) && r1_rob_tag === cdb_rob_tag(c)) -> cdb_data(c)))

      next_entries(target_fu_idx).q1_ready := !r1_pending || op.rs1 === 0.U || r1_cdb_valid
      next_entries(target_fu_idx).q1_tag   := r1_rob_tag
      next_entries(target_fu_idx).v1       := Mux(r1_cdb_valid, r1_cdb_data, op.rs1_data)

      val r2_pending   = temp_reg_valid(w)(op.rs2)
      val r2_rob_tag   = temp_reg_rob(w)(op.rs2)
      val r2_cdb_valid = (0 until numFUs).map(c => cdb_valid(c) && r2_rob_tag === cdb_rob_tag(c)).foldLeft(false.B)(_ || _)
      val r2_cdb_data  = Mux1H((0 until numFUs).map(c => (cdb_valid(c) && r2_rob_tag === cdb_rob_tag(c)) -> cdb_data(c)))

      next_entries(target_fu_idx).q2_ready := !r2_pending || op.rs2 === 0.U || r2_cdb_valid
      next_entries(target_fu_idx).q2_tag   := r2_rob_tag
      next_entries(target_fu_idx).v2       := Mux(r2_cdb_valid, r2_cdb_data, op.rs2_data)
    }
  }

  // Update Pending Registers
  val next_reg_valid = Wire(Vec(numRegs, Bool()))
  for (r <- 0 until numRegs) {
    val pending_rob = temp_reg_rob(p(IssueWidth))(r)
    val clears      = (0 until numFUs).map(c => cdb_valid(c) && cdb_rob_tag(c) === pending_rob && cdb_rd(c) === r.U).foldLeft(false.B)(_ || _)

    val issued_this_cycle = temp_reg_valid(p(IssueWidth))(r) && (!reg_pending_valid(r) || reg_pending_rob(r) =/= pending_rob)

    next_reg_valid(r) := Mux(issued_this_cycle, true.B, Mux(clears, false.B, temp_reg_valid(p(IssueWidth))(r)))
  }

  when(flush) {
    reg_pending_valid.foreach(_ := false.B)
    entries.foreach(_.valid := false.B)
  }.otherwise {
    reg_pending_valid := next_reg_valid
    reg_pending_rob   := temp_reg_rob(p(IssueWidth))
    entries           := next_entries
  }
}
