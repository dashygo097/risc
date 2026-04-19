package arch.core.div

import arch.configs._
import chisel3._
import vopts.math.RestoringDivider

object RV32IMDivUtilities extends RegisteredUtilities[DivUtilities] {
  override def utils: DivUtilities = new DivUtilities {
    override def name: String = "rv32im"

    override def fn(en: Bool, kill: Bool, src1: UInt, src2: UInt, is_signed: Bool, is_rem: Bool): (UInt, Bool, Bool) = {
      val result = WireDefault(0.U(p(XLen).W))
      val busy = WireDefault(false.B)
      val done = WireDefault(true.B)

      val div = Module(new RestoringDivider(p(XLen)))

      div.start := en
      div.kill := kill
      div.dividend := src1
      div.divisor := src2
      div.is_signed := is_signed
      div.select_remainder := is_rem

      result := div.result
      busy := div.busy
      done := div.done

      (result, busy, done)
    }
  }

  override def factory: UtilitiesFactory[DivUtilities] = DivUtilitiesFactory
}
