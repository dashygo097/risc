package arch.core.mult

import arch.configs._
import chisel3._

object RV32IMultUtilities extends RegisteredUtilities[MultUtilities] {
  override def utils: MultUtilities = new MultUtilities {
    override def name: String = "rv32i"

    // NOTE: Should not impled, return NULL
    override def decodeUop(uop: UInt): MultCtrl                                                                                   = 0.U.asTypeOf(new MultCtrl)
    override def fn(en: Bool, kill: Bool, src1: UInt, src2: UInt, a_signed: Bool, b_signed: Bool, high: Bool): (UInt, Bool, Bool) = (0.U(p(XLen).W), false.B, true.B)
  }

  override def factory: UtilitiesFactory[MultUtilities] = MultUtilitiesFactory
}
