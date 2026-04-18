package arch.core.mult.riscv

import arch.core.mult._
import arch.configs._
import chisel3._

object RV32IMultUtils extends RegisteredUtils[MultUtils] {
  override def utils: MultUtils = new MultUtils {
    override def name: String = "rv32i"

    // NOTE: Should not impled, return NULL
    override def decode(uop: UInt): MultCtrl                                                                                      = 0.U.asTypeOf(new MultCtrl)
    override def fn(en: Bool, kill: Bool, src1: UInt, src2: UInt, a_signed: Bool, b_signed: Bool, high: Bool): (UInt, Bool, Bool) = (0.U(p(XLen).W), false.B, true.B)
  }

  override def factory: UtilsFactory[MultUtils] = MultUtilsFactory
}
