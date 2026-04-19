package arch.core.div

import arch.configs._
import chisel3._

trait DivUtils extends Utils {
  def decode(uop: UInt): DivCtrl
  def fn(en: Bool, kill: Bool, src1: UInt, src2: UInt, is_signed: Bool, is_rem: Bool): (UInt, Bool, Bool)
}

object DivUtilsFactory extends UtilsFactory[DivUtils]("Div")

object DivInit {
  val rv32iUtils  = RV32IDivUtils
  val rv32imUtils = RV32IMDivUtils
}
