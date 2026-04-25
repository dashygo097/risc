package arch.core.ooo

import arch.configs._
import chisel3._
import chisel3.util.{ log2Ceil, PopCount, MuxCase }

class RobEnqIO(implicit p: Parameters) extends Bundle {
  val valid            = Input(Bool())
  val ready            = Output(Bool())
  val pc               = Input(UInt(p(XLen).W))
  val instr            = Input(UInt(p(ILen).W))
  val rd               = Input(UInt(log2Ceil(p(NumArchRegs)).W))
  val pd               = Input(UInt(log2Ceil(p(NumPhyRegs)).W))
  val old_pd           = Input(UInt(log2Ceil(p(NumPhyRegs)).W))
  val is_branch        = Input(Bool())
  val is_lsu           = Input(Bool())
  val bpu_pred_taken   = Input(Bool())
  val bpu_pred_target  = Input(UInt(p(XLen).W))
  val bpu_pht_index    = Input(UInt(p(GShareGhrWidth).W))
  val bpu_ghr_snapshot = Input(UInt(p(GShareGhrWidth).W))
  val rob_tag          = Output(UInt(log2Ceil(p(ROBSize)).W))
}

class RobWbIO(implicit p: Parameters) extends Bundle {
  val valid         = Input(Bool())
  val rob_tag       = Input(UInt(log2Ceil(p(ROBSize)).W))
  val data          = Input(UInt(p(XLen).W))
  val is_bru        = Input(Bool())
  val actual_taken  = Input(Bool())
  val actual_target = Input(UInt(p(XLen).W))
  val trap_req      = Input(Bool())
  val trap_target   = Input(UInt(p(XLen).W))
  val trap_ret      = Input(Bool())
  val trap_ret_tgt  = Input(UInt(p(XLen).W))
}

class RobCommitIO(implicit p: Parameters) extends Bundle {
  val valid             = Output(Bool())
  val pop               = Input(Bool())
  val pc                = Output(UInt(p(XLen).W))
  val instr             = Output(UInt(p(ILen).W))
  val rd                = Output(UInt(log2Ceil(p(NumArchRegs)).W))
  val data              = Output(UInt(p(XLen).W))
  val pd                = Output(UInt(log2Ceil(p(NumPhyRegs)).W))
  val old_pd            = Output(UInt(log2Ceil(p(NumPhyRegs)).W))
  val flush_pipeline    = Output(Bool())
  val flush_target      = Output(UInt(p(XLen).W))
  val is_branch         = Output(Bool())
  val is_lsu            = Output(Bool())
  val bpu_pred_taken    = Output(Bool())
  val bpu_pred_target   = Output(UInt(p(XLen).W))
  val bpu_actual_taken  = Output(Bool())
  val bpu_actual_target = Output(UInt(p(XLen).W))
  val bpu_pht_index     = Output(UInt(p(GShareGhrWidth).W))
  val bpu_ghr_snapshot  = Output(UInt(p(GShareGhrWidth).W))
}

class RobBypassIO(implicit p: Parameters) extends Bundle {
  val valid   = Output(Bool())
  val data    = Output(UInt(p(XLen).W))
  val pending = Output(Bool())
}

class ROBEntry(implicit p: Parameters) extends Bundle {
  val valid          = Bool()
  val ready          = Bool()
  val pc             = UInt(p(XLen).W)
  val instr          = UInt(p(ILen).W)
  val rd             = UInt(log2Ceil(p(NumArchRegs)).W)
  val data           = UInt(p(XLen).W)
  val pd             = UInt(log2Ceil(p(NumPhyRegs)).W)
  val old_pd         = UInt(log2Ceil(p(NumPhyRegs)).W)
  val is_branch      = Bool()
  val is_lsu         = Bool()
  val pred_taken     = Bool()
  val pred_target    = UInt(p(XLen).W)
  val pht_index      = UInt(p(GShareGhrWidth).W)
  val ghr_snapshot   = UInt(p(GShareGhrWidth).W)
  val actual_taken   = Bool()
  val actual_target  = UInt(p(XLen).W)
  val flush_pipeline = Bool()
  val flush_target   = UInt(p(XLen).W)
}

class ReorderBuffer(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_rob"

  val io = IO(new Bundle {
    val enq    = Vec(p(IssueWidth), new RobEnqIO)
    val wb     = Vec(p(FunctionalUnits).size, new RobWbIO)
    val commit = Vec(p(IssueWidth), new RobCommitIO)

    val read_rob_tag = Input(Vec(p(IssueWidth), UInt(log2Ceil(p(ROBSize)).W)))
    val read_pd      = Output(Vec(p(IssueWidth), UInt(log2Ceil(p(NumPhyRegs)).W)))

    val rs1_addr   = Input(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
    val rs1_bypass = Vec(p(IssueWidth), new RobBypassIO)
    val rs2_addr   = Input(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
    val rs2_bypass = Vec(p(IssueWidth), new RobBypassIO)

    val empty = Output(Bool())
    val flush = Input(Bool())
  })

  val buffer = RegInit(VecInit(Seq.fill(p(ROBSize))(0.U.asTypeOf(new ROBEntry))))
  val head   = RegInit(0.U(log2Ceil(p(ROBSize)).W))
  val tail   = RegInit(0.U(log2Ceil(p(ROBSize)).W))
  val count  = RegInit(0.U(log2Ceil(p(ROBSize) + 1).W))

  io.empty := count === 0.U
  val available_slots = p(ROBSize).U - count

  val enq_valids = io.enq.map(_.valid)
  val enq_count  = PopCount(enq_valids)

  val enq_offsets = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(ROBSize)).W)))
  enq_offsets(0)   := 0.U
  for (w <- 1 until p(IssueWidth))
    enq_offsets(w) := (enq_offsets(w - 1) + enq_valids(w - 1).asUInt)(log2Ceil(p(ROBSize)) - 1, 0)

  for (w <- 0 until p(IssueWidth)) {
    io.enq(w).ready := available_slots > w.U
    val idx = ((tail + enq_offsets(w)) % p(ROBSize).U)(log2Ceil(p(ROBSize)) - 1, 0)
    io.enq(w).rob_tag := idx

    when(io.enq(w).valid) {
      buffer(idx).valid          := true.B
      buffer(idx).ready          := false.B
      buffer(idx).pc             := io.enq(w).pc
      buffer(idx).instr          := io.enq(w).instr
      buffer(idx).rd             := io.enq(w).rd
      buffer(idx).pd             := io.enq(w).pd
      buffer(idx).old_pd         := io.enq(w).old_pd
      buffer(idx).is_branch      := io.enq(w).is_branch
      buffer(idx).is_lsu         := io.enq(w).is_lsu
      buffer(idx).pred_taken     := io.enq(w).bpu_pred_taken
      buffer(idx).pred_target    := io.enq(w).bpu_pred_target
      buffer(idx).pht_index      := io.enq(w).bpu_pht_index
      buffer(idx).ghr_snapshot   := io.enq(w).bpu_ghr_snapshot
      buffer(idx).actual_taken   := false.B
      buffer(idx).actual_target  := 0.U
      buffer(idx).flush_pipeline := false.B
      buffer(idx).flush_target   := 0.U
    }
  }

  for (i <- 0 until p(FunctionalUnits).size)
    when(io.wb(i).valid) {
      val wb_entry = buffer(io.wb(i).rob_tag)
      wb_entry.ready := true.B
      wb_entry.data  := io.wb(i).data

      val bru_mispredict = io.wb(i).is_bru && (
        (io.wb(i).actual_taken =/= wb_entry.pred_taken) ||
          (io.wb(i).actual_taken && io.wb(i).actual_target =/= wb_entry.pred_target)
      )

      val non_bru_mispredict = !io.wb(i).is_bru && wb_entry.pred_taken
      val is_mispredict      = bru_mispredict || non_bru_mispredict

      when(io.wb(i).is_bru) {
        wb_entry.actual_taken  := io.wb(i).actual_taken
        wb_entry.actual_target := io.wb(i).actual_target
      }.elsewhen(non_bru_mispredict) {
        wb_entry.actual_taken  := false.B
        wb_entry.actual_target := wb_entry.pc + p(IAlign).U
      }

      val target = Mux(io.wb(i).is_bru, io.wb(i).actual_target, wb_entry.pc + p(IAlign).U)

      wb_entry.flush_pipeline := is_mispredict || io.wb(i).trap_req || io.wb(i).trap_ret
      wb_entry.flush_target   := MuxCase(
        target,
        Seq(
          io.wb(i).trap_req -> io.wb(i).trap_target,
          io.wb(i).trap_ret -> io.wb(i).trap_ret_tgt
        )
      )
    }

  var stop_commit      = false.B
  var commit_valid_acc = true.B
  for (w <- 0 until p(IssueWidth)) {
    val idx          = ((head + w.U) % p(ROBSize).U)(log2Ceil(p(ROBSize)) - 1, 0)
    val isCondBranch = buffer(idx).is_branch && buffer(idx).instr(6, 0) === "b1100011".U

    val committable = (count > w.U) && buffer(idx).valid && buffer(idx).ready && !stop_commit && commit_valid_acc

    io.commit(w).valid             := committable
    io.commit(w).pc                := buffer(idx).pc
    io.commit(w).instr             := buffer(idx).instr
    io.commit(w).rd                := buffer(idx).rd
    io.commit(w).data              := buffer(idx).data
    io.commit(w).pd                := buffer(idx).pd
    io.commit(w).old_pd            := buffer(idx).old_pd
    io.commit(w).flush_pipeline    := buffer(idx).flush_pipeline
    io.commit(w).flush_target      := buffer(idx).flush_target
    io.commit(w).is_branch         := buffer(idx).is_branch
    io.commit(w).is_lsu            := buffer(idx).is_lsu
    io.commit(w).bpu_pred_taken    := buffer(idx).pred_taken
    io.commit(w).bpu_pred_target   := buffer(idx).pred_target
    io.commit(w).bpu_actual_taken  := buffer(idx).actual_taken
    io.commit(w).bpu_actual_target := buffer(idx).actual_target
    io.commit(w).bpu_pht_index     := buffer(idx).pht_index
    io.commit(w).bpu_ghr_snapshot  := buffer(idx).ghr_snapshot

    when(committable && (buffer(idx).flush_pipeline || isCondBranch)) { stop_commit = true.B }
    commit_valid_acc = committable

    when(io.commit(w).pop) {
      buffer(idx).valid := false.B
    }
  }

  val commit_pops  = io.commit.map(_.pop)
  val commit_count = PopCount(commit_pops)

  head  := ((head + commit_count) % p(ROBSize).U)(log2Ceil(p(ROBSize)) - 1, 0)
  tail  := ((tail + enq_count)    % p(ROBSize).U)(log2Ceil(p(ROBSize)) - 1, 0)
  count := count + enq_count - commit_count

  when(io.flush) {
    head                                          := 0.U
    tail                                          := 0.U
    count                                         := 0.U
    for (i <- 0 until p(ROBSize)) buffer(i).valid := false.B
  }

  for (w <- 0 until p(IssueWidth))
    io.read_pd(w) := buffer(io.read_rob_tag(w)).pd

  def bypass(rs: UInt): (Bool, UInt) = {
    val valid_out = WireDefault(false.B)
    val data_out  = WireDefault(0.U(p(XLen).W))
    for (d <- p(ROBSize) to 1 by -1) {
      val idx   = ((tail + p(ROBSize).U - d.U) % p(ROBSize).U)(log2Ceil(p(ROBSize)) - 1, 0)
      val entry = buffer(idx)
      when(entry.valid && entry.rd === rs && rs =/= 0.U) {
        when(entry.ready) {
          valid_out := true.B
          data_out  := entry.data
        }.otherwise {
          valid_out := false.B
        }
      }
    }
    (valid_out, data_out)
  }

  def pending(rs: UInt): Bool = {
    val match_mask = Wire(Vec(p(ROBSize), Bool()))
    for (i <- 0 until p(ROBSize))
      match_mask(i) := buffer(i).valid && buffer(i).rd === rs && rs =/= 0.U && !buffer(i).ready
    match_mask.asUInt.orR
  }

  for (w <- 0 until p(IssueWidth)) {
    val (rs1_v, rs1_d) = bypass(io.rs1_addr(w))
    io.rs1_bypass(w).valid   := rs1_v
    io.rs1_bypass(w).data    := rs1_d
    io.rs1_bypass(w).pending := pending(io.rs1_addr(w))

    val (rs2_v, rs2_d) = bypass(io.rs2_addr(w))
    io.rs2_bypass(w).valid   := rs2_v
    io.rs2_bypass(w).data    := rs2_d
    io.rs2_bypass(w).pending := pending(io.rs2_addr(w))
  }
}
