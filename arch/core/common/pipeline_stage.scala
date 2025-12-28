package arch.core.common

import arch.configs._
import chisel3._

class PipelineStage[T <: Data](gen: T)(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_pipeline_stage_${gen.getClass.getSimpleName}"

  val stall = IO(Input(Bool()))
  val flush = IO(Input(Bool()))
  val sin   = IO(Input(gen.cloneType))
  val sout  = IO(Output(gen.cloneType))

  val sreg = RegInit(0.U.asTypeOf(gen))

  when(flush) {
    sreg := 0.U.asTypeOf(gen)
  }.elsewhen(!stall) {
    sreg := sin
  }

  sout := sreg
}
