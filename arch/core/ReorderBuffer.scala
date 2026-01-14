package arch.core

import chisel3._
import chisel3.util._
import arch.configs._

class ReorderBuffer(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    // Allocation
    val alloc_en  = Input(Bool())
    val alloc_idx = Output(UInt(log2Ceil(p(ROBSize)).W))
    val full      = Output(Bool())

    // Allocation info
    val alloc_pc       = Input(UInt(p(XLen).W))
    val alloc_arch_reg = Input(UInt(log2Ceil(p(NumArchRegs)).W))
    val alloc_old_preg = Input(UInt(log2Ceil(p(NumPhyRegs)).W))
    val alloc_new_preg = Input(UInt(log2Ceil(p(NumPhyRegs)).W))
    val alloc_uses_rd  = Input(Bool())

    // Finish
    val complete_en  = Input(Bool())
    val complete_idx = Input(UInt(log2Ceil(p(ROBSize)).W))

    // Commit
    val commit_en       = Output(Bool())
    val commit_idx      = Output(UInt(log2Ceil(p(ROBSize)).W))
    val commit_arch_reg = Output(UInt(log2Ceil(p(NumArchRegs)).W))
    val commit_old_preg = Output(UInt(log2Ceil(p(NumPhyRegs)).W))
    val commit_new_preg = Output(UInt(log2Ceil(p(NumPhyRegs)).W))
    val commit_uses_rd  = Output(Bool())

    // Flush
    val flush     = Input(Bool())
    val flush_idx = Input(UInt(log2Ceil(p(ROBSize)).W))
  })

  class ROBEntry extends Bundle {
    val valid    = Bool()
    val complete = Bool()
    val pc       = UInt(p(XLen).W)
    val arch_reg = UInt(log2Ceil(p(NumArchRegs)).W)
    val old_preg = UInt(log2Ceil(p(NumPhyRegs)).W)
    val new_preg = UInt(log2Ceil(p(NumPhyRegs)).W)
    val uses_rd  = Bool()
  }

  val entries = RegInit(VecInit(Seq.fill(p(ROBSize))(0.U.asTypeOf(new ROBEntry))))
  val head    = RegInit(0.U(log2Ceil(p(ROBSize)).W))
  val tail    = RegInit(0.U(log2Ceil(p(ROBSize)).W))

  val count = RegInit(0.U(log2Ceil(p(ROBSize) + 1).W))
  io.full := count >= p(ROBSize).U

  val can_commit = entries(head).valid && entries(head).complete

  // Allocation
  io.alloc_idx := tail
  when(io.alloc_en && !io.full) {
    entries(tail).valid    := true.B
    entries(tail).complete := false.B
    entries(tail).pc       := io.alloc_pc
    entries(tail).arch_reg := io.alloc_arch_reg
    entries(tail).old_preg := io.alloc_old_preg
    entries(tail).new_preg := io.alloc_new_preg
    entries(tail).uses_rd  := io.alloc_uses_rd

    tail  := tail + 1.U
    count := count + 1.U
  }

  // Finish
  when(io.complete_en) {
    entries(io.complete_idx).complete := true.B
  }

  // Commit
  io.commit_en       := can_commit
  io.commit_idx      := head
  io.commit_arch_reg := entries(head).arch_reg
  io.commit_old_preg := entries(head).old_preg
  io.commit_new_preg := entries(head).new_preg
  io.commit_uses_rd  := entries(head).uses_rd

  when(can_commit) {
    entries(head).valid := false.B
    head                := head + 1.U
    count               := count - 1.U
  }

  // Flush
  when(io.flush) {
    tail := io.flush_idx
  }
}
