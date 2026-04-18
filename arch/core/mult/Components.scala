package arch.core.mult

import arch.configs._
import chisel3._

trait MultUtilities extends Utilities {
  def decode(uop: UInt): MultCtrl
  def fn(en: Bool, kill: Bool, src1: UInt, src2: UInt, a_signed: Bool, b_signed: Bool, high: Bool): (UInt, Bool, Bool)
}

object MultUtilitiesFactory extends UtilitiesFactory[MultUtilities]("Mult")

object MultInit {
  val rv32iUtils  = RV32IMultUtilities
  val rv32imUtils = RV32IMMultUtilities
}
