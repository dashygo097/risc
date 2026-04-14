package arch.core.mult

import arch.configs._
import vopts.math.Multiplier
import chisel3._

object RV32IMMultUtilities extends RegisteredUtilities[MultUtilities] {
  override def utils: MultUtilities = new MultUtilities {
    override def name: String = "rv32im"

    override def fn(en: Bool, kill: Bool, src1: UInt, src2: UInt, a_signed: Bool, b_signed: Bool, high: Bool): (UInt, Bool, Bool) = {
      val result = WireDefault(0.U(p(XLen).W))
      val busy   = WireDefault(false.B)
      val done   = WireDefault(true.B)

      val mult = Module(new Multiplier(p(XLen), p(MultPipelineStages)))

      mult.start        := en
      mult.kill         := kill
      mult.multiplicand := src1
      mult.multiplier   := src2
      mult.a_signed     := a_signed
      mult.b_signed     := b_signed
      mult.take_high    := high

      result := mult.result
      done   := mult.done
      busy   := mult.busy

      (result, busy, done)
    }
  }

  override def factory: UtilitiesFactory[MultUtilities] = MultUtilitiesFactory
}
