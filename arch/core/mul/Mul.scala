package arch.core.mul

import arch.configs._
import chisel3._

class Mul(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_multiplier"

  val utils = MulUtilitiesFactory.getOrThrow(p(ISA))
  val io    = IO(new MulIO)

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
