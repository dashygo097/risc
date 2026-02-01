package arch.core.common

import _root_.arch.configs._
import chisel3._

class PipelineStage[T <: Data](gen: T)(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_pipeline_stage_${gen.getClass.getSimpleName}"

  val stall = IO(Input(Bool()))
  val flush = IO(Input(Bool()))
  val busy  = IO(Output(Bool()))

  val sin_instr  = IO(Input(UInt(p(ILen).W)))
  val sin_extra  = IO(Input(gen.cloneType))
  val sout_instr = IO(Output(UInt(p(ILen).W)))
  val sout_extra = IO(Output(gen.cloneType))

  val sinstr_reg  = RegInit(p(Bubble).value.U(p(ILen).W))
  val sextra_regs = RegInit(0.U.asTypeOf(gen))

  when(flush) {
    sinstr_reg  := p(Bubble).value.U(p(ILen).W)
    sextra_regs := 0.U.asTypeOf(gen)
  }.elsewhen(!stall) {
    sinstr_reg  := sin_instr
    sextra_regs := sin_extra
  }

  busy := sinstr_reg =/= p(Bubble).value.U(p(ILen).W)

  sout_instr := sinstr_reg
  sout_extra := sextra_regs
}
