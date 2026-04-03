package arch.core.mult

import arch.configs._
import chisel3._

class Mult(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_multiplier"

  val utils = MultUtilitiesFactory.getOrThrow(p(ISA))
  val io    = IO(new MultIO)

  val mulImpl = utils.build

  mulImpl.start        := io.en
  mulImpl.kill         := io.kill
  mulImpl.multiplicand := io.src1
  mulImpl.multiplier   := io.src2
  mulImpl.a_signed     := io.a_signed
  mulImpl.b_signed     := io.b_signed
  mulImpl.take_high    := io.high

  io.result := mulImpl.result
  io.done   := mulImpl.done
  io.busy   := mulImpl.busy
}
