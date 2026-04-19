package arch.core.div

import arch.configs._
import chisel3._

trait DivUtilities extends Utilities {
  def fn(en: Bool, kill: Bool, src1: UInt, src2: UInt, is_signed: Bool, is_rem: Bool): (UInt, Bool, Bool)
}

object DivUtilitiesFactory extends UtilitiesFactory[DivUtilities]("Div")

object DivInit {
  val rv32iUtils  = RV32IDivUtilities
  val rv32imUtils = RV32IMDivUtilities
}
