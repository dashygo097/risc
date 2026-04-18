package arch.core.lsu

import arch.configs._
import chisel3._

trait LsuUtils extends Utils {
  def decode(uop: UInt): LsuCtrl
}

object LsuUtilsFactory extends UtilsFactory[LsuUtils]("LSU")

object LsuInit {
  val rv32iUtils  = riscv.RV32ILsuUtils
  val rv32imUtils = riscv.RV32IMLsuUtils
}
