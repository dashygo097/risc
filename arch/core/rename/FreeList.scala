package arch.core.rename

import arch.configs._
import chisel3._
import chisel3.util._

class FreeList(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    // Allocate physical register
    val alloc_req   = Input(Bool())
    val alloc_preg  = Output(UInt(log2Ceil(p(NumPhyRegs)).W))
    val alloc_valid = Output(Bool())

    // Free physical register
    val free_en   = Input(Bool())
    val free_preg = Input(UInt(log2Ceil(p(NumPhyRegs)).W))

    val empty = Output(Bool())
  })

  val free_vec = RegInit(VecInit(Seq.tabulate(p(NumPhyRegs)) { i =>
    (i >= p(NumArchRegs)).B
  }))

  // Find first free physical register
  val free_preg = PriorityEncoder(free_vec)
  val has_free  = free_vec.asUInt.orR

  io.alloc_preg  := free_preg
  io.alloc_valid := has_free
  io.empty       := !has_free

  // Allocate
  when(io.alloc_req && has_free) {
    free_vec(free_preg) := false.B
  }

  // Free
  when(io.free_en) {
    free_vec(io.free_preg) := true.B
  }
}
