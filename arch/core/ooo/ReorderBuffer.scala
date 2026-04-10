package arch.core.ooo

import arch.configs._
import chisel3._
import chisel3.util._

class ROBEntry extends Bundle {
  val valid          = Bool()
  val ready          = Bool()
  val pc             = UInt(p(XLen).W)
  val instr          = UInt(p(ILen).W)
  val rd             = UInt(log2Ceil(p(NumArchRegs)).W)
  val data           = UInt(p(XLen).W)
  val pd             = UInt(log2Ceil(p(NumPhyRegs)).W)
  val old_pd         = UInt(log2Ceil(p(NumPhyRegs)).W)
  val is_branch      = Bool()
  val pred_taken     = Bool()
  val pred_target    = UInt(p(XLen).W)
  val actual_taken   = Bool()
  val actual_target  = UInt(p(XLen).W)
  val flush_pipeline = Bool()
  val flush_target   = UInt(p(XLen).W)
}

class ReorderBuffer(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_rob"

  val io = IO(new Bundle {
    val enq_valid           = Input(Bool())
    val enq_pc              = Input(UInt(p(XLen).W))
    val enq_instr           = Input(UInt(p(ILen).W))
    val enq_rd              = Input(UInt(log2Ceil(p(NumArchRegs)).W))
    val enq_pd              = Input(UInt(log2Ceil(p(NumPhyRegs)).W))
    val enq_old_pd          = Input(UInt(log2Ceil(p(NumPhyRegs)).W))
    val enq_is_branch       = Input(Bool())
    val enq_bpu_pred_taken  = Input(Bool())
    val enq_bpu_pred_target = Input(UInt(p(XLen).W))
    val enq_ready           = Output(Bool())
    val rob_tag             = Output(UInt(log2Ceil(p(ROBSize)).W))

    val wb_valid         = Input(Bool())
    val wb_rob_tag       = Input(UInt(log2Ceil(p(ROBSize)).W))
    val wb_data          = Input(UInt(p(XLen).W))
    val wb_is_bru        = Input(Bool())
    val wb_actual_taken  = Input(Bool())
    val wb_actual_target = Input(UInt(p(XLen).W))
    val wb_trap_req      = Input(Bool())
    val wb_trap_target   = Input(UInt(p(XLen).W))
    val wb_trap_ret      = Input(Bool())
    val wb_trap_ret_tgt  = Input(UInt(p(XLen).W))

    val read_rob_tag = Input(UInt(log2Ceil(p(ROBSize)).W))
    val read_pd      = Output(UInt(log2Ceil(p(NumPhyRegs)).W))

    val commit_valid             = Output(Bool())
    val commit_pc                = Output(UInt(p(XLen).W))
    val commit_instr             = Output(UInt(p(ILen).W))
    val commit_rd                = Output(UInt(log2Ceil(p(NumArchRegs)).W))
    val commit_data              = Output(UInt(p(XLen).W))
    val commit_pd                = Output(UInt(log2Ceil(p(NumPhyRegs)).W))
    val commit_old_pd            = Output(UInt(log2Ceil(p(NumPhyRegs)).W))
    val commit_flush_pipeline    = Output(Bool())
    val commit_flush_target      = Output(UInt(p(XLen).W))
    val commit_is_branch         = Output(Bool())
    val commit_bpu_actual_taken  = Output(Bool())
    val commit_bpu_actual_target = Output(UInt(p(XLen).W))
    val commit_pop               = Input(Bool())

    val rs1_addr         = Input(UInt(log2Ceil(p(NumArchRegs)).W))
    val rs1_bypass_valid = Output(Bool())
    val rs1_bypass_data  = Output(UInt(p(XLen).W))
    val rs1_pending      = Output(Bool())

    val rs2_addr         = Input(UInt(log2Ceil(p(NumArchRegs)).W))
    val rs2_bypass_valid = Output(Bool())
    val rs2_bypass_data  = Output(UInt(p(XLen).W))
    val rs2_pending      = Output(Bool())

    val empty = Output(Bool())
    val flush = Input(Bool())
  })

  val buffer = RegInit(VecInit(Seq.fill(p(ROBSize))(0.U.asTypeOf(new ROBEntry))))

  val head       = RegInit(0.U(log2Ceil(p(ROBSize)).W))
  val tail       = RegInit(0.U(log2Ceil(p(ROBSize)).W))
  val maybe_full = RegInit(false.B)

  val empty = (head === tail) && !maybe_full
  val full  = (head === tail) && maybe_full

  io.empty     := empty
  io.enq_ready := !full
  io.rob_tag   := tail

  io.read_pd := buffer(io.read_rob_tag).pd

  when(io.enq_valid && !full) {
    buffer(tail).valid          := true.B
    buffer(tail).ready          := false.B
    buffer(tail).pc             := io.enq_pc
    buffer(tail).instr          := io.enq_instr
    buffer(tail).rd             := io.enq_rd
    buffer(tail).pd             := io.enq_pd
    buffer(tail).old_pd         := io.enq_old_pd
    buffer(tail).is_branch      := io.enq_is_branch
    buffer(tail).pred_taken     := io.enq_bpu_pred_taken
    buffer(tail).pred_target    := io.enq_bpu_pred_target
    buffer(tail).actual_taken   := false.B
    buffer(tail).actual_target  := 0.U
    buffer(tail).flush_pipeline := false.B
    buffer(tail).flush_target   := 0.U
    tail                        := tail + 1.U
    maybe_full                  := (tail + 1.U === head)
  }

  when(io.wb_valid) {
    val wb_entry = buffer(io.wb_rob_tag)
    wb_entry.ready := true.B
    wb_entry.data  := io.wb_data

    val is_mispredict = io.wb_is_bru && (
      (io.wb_actual_taken =/= wb_entry.pred_taken) ||
        (io.wb_actual_taken && io.wb_actual_target =/= wb_entry.pred_target)
    )

    when(io.wb_is_bru) {
      wb_entry.actual_taken  := io.wb_actual_taken
      wb_entry.actual_target := io.wb_actual_target
    }

    wb_entry.flush_pipeline := is_mispredict || io.wb_trap_req || io.wb_trap_ret
    wb_entry.flush_target   := MuxCase(
      io.wb_actual_target,
      Seq(
        io.wb_trap_req -> io.wb_trap_target,
        io.wb_trap_ret -> io.wb_trap_ret_tgt
      )
    )
  }

  val head_entry = buffer(head)
  io.commit_valid             := !empty && head_entry.ready
  io.commit_pc                := head_entry.pc
  io.commit_instr             := head_entry.instr
  io.commit_rd                := head_entry.rd
  io.commit_data              := head_entry.data
  io.commit_pd                := head_entry.pd
  io.commit_old_pd            := head_entry.old_pd
  io.commit_flush_pipeline    := head_entry.flush_pipeline
  io.commit_flush_target      := head_entry.flush_target
  io.commit_is_branch         := head_entry.is_branch
  io.commit_bpu_actual_taken  := head_entry.actual_taken
  io.commit_bpu_actual_target := head_entry.actual_target

  when(io.commit_pop && io.commit_valid) {
    buffer(head).valid := false.B
    head               := head + 1.U
    maybe_full         := false.B
  }

  when(io.flush) {
    head                                          := 0.U
    tail                                          := 0.U
    maybe_full                                    := false.B
    for (i <- 0 until p(ROBSize)) buffer(i).valid := false.B
  }

  def bypass(rs: UInt): (Bool, UInt) = {
    val match_mask = Wire(Vec(p(ROBSize), Bool()))
    for (i <- 0 until p(ROBSize))
      match_mask(i) := buffer(i).valid && buffer(i).rd === rs && rs =/= 0.U && buffer(i).ready

    val valid_out = WireDefault(false.B)
    val data_out  = WireDefault(0.U(p(XLen).W))

    val robBits = log2Ceil(p(ROBSize))
    for (d <- p(ROBSize) to 1 by -1) {
      val idx = ((tail + p(ROBSize).U - d.U) % p(ROBSize).U)(robBits - 1, 0)
      when(match_mask(idx)) {
        valid_out := true.B
        data_out  := buffer(idx).data
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

  val (rs1_v, rs1_d) = bypass(io.rs1_addr)
  io.rs1_bypass_valid := rs1_v
  io.rs1_bypass_data  := rs1_d
  io.rs1_pending      := pending(io.rs1_addr)

  val (rs2_v, rs2_d) = bypass(io.rs2_addr)
  io.rs2_bypass_valid := rs2_v
  io.rs2_bypass_data  := rs2_d
  io.rs2_pending      := pending(io.rs2_addr)
}
