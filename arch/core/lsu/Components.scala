package arch.core.lsu

import arch.configs._
import chisel3._

trait LsuUtilities extends Utilities {
  def decode(uop: UInt): LsuCtrl
}

object LsuUtilitiesFactory extends UtilitiesFactory[LsuUtilities]("LSU")

object LsuInit {
  val rv32iUtils  = RV32ILsuUtilities
  val rv32imUtils = RV32IMLsuUtilities
}
