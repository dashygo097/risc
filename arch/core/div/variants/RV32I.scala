package arch.core.div

import arch.configs._
import chisel3._

object RV32IDivUtilities extends RegisteredUtilities[DivUtilities] {
  override def utils: DivUtilities = new DivUtilities {
    override def name: String = "rv32i"

    // RV32I has no integer divide extension.
    override def fn(en: Bool, kill: Bool, src1: UInt, src2: UInt, is_signed: Bool, is_rem: Bool): (UInt, Bool, Bool) =
      (0.U(p(XLen).W), false.B, true.B)
  }

  override def factory: UtilitiesFactory[DivUtilities] = DivUtilitiesFactory
}
