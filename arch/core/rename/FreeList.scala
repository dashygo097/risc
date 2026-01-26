package arch.core.rename

import arch.configs._
import chisel3._
import chisel3.util._

class FreeList(implicit p: Parameters) extends Module {
  // Allocate physical register
  val alloc_en    = IO(Input(Bool()))
  val alloc_preg  = IO(Output(UInt(log2Ceil(p(NumPhyRegs)).W)))
  val alloc_valid = IO(Output(Bool()))

  // Free physical register
  val free_en   = IO(Input(Bool()))
  val free_preg = IO(Input(UInt(log2Ceil(p(NumPhyRegs)).W)))

  val empty = IO(Output(Bool()))

  val free_vec = RegInit(VecInit(Seq.tabulate(p(NumPhyRegs)) { i =>
    (i >= p(NumArchRegs)).B
  }))

  // Find first free physical register
  val first_free_preg = PriorityEncoder(free_vec)
  val has_free        = free_vec.asUInt.orR

  alloc_preg  := first_free_preg
  alloc_valid := has_free
  empty       := !has_free

  // Allocate
  when(alloc_en && has_free) {
    free_vec(first_free_preg) := false.B
  }

  // Free
  when(free_en) {
    free_vec(free_preg) := true.B
  }
}
