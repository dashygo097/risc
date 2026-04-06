package arch.core.ooo

import arch.configs._
import chisel3._
import chisel3.util.{ PriorityEncoder, log2Ceil }

class FreeList(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_free_list"

  val io = IO(new Bundle {
    // Speculative Allocation
    val alloc_en    = Input(Bool())
    val alloc_preg  = Output(UInt(log2Ceil(p(NumPhyRegs)).W))
    val alloc_valid = Output(Bool())

    // Architectural Free
    val commit_free_en   = Input(Bool())
    val commit_free_preg = Input(UInt(log2Ceil(p(NumPhyRegs)).W))

    // Rollbacks
    val flush = Input(Bool())
    val empty = Output(Bool())
  })

  val arch_free_vec = RegInit(VecInit(Seq.tabulate(p(NumPhyRegs))(i => (i >= p(NumArchRegs)).B)))
  val spec_free_vec = RegInit(VecInit(Seq.tabulate(p(NumPhyRegs))(i => (i >= p(NumArchRegs)).B)))

  val first_free_preg = PriorityEncoder(spec_free_vec)
  val has_free        = spec_free_vec.asUInt.orR

  io.alloc_preg  := first_free_preg
  io.alloc_valid := has_free
  io.empty       := !has_free

  when(io.flush) {
    spec_free_vec := arch_free_vec
  }.otherwise {
    when(io.alloc_en && has_free) {
      spec_free_vec(first_free_preg) := false.B
    }
    when(io.commit_free_en) {
      arch_free_vec(io.commit_free_preg) := true.B
      spec_free_vec(io.commit_free_preg) := true.B
    }
  }
}
