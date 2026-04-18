package arch.core.mult

import arch.configs._
import chisel3._

trait MultUtils extends Utils {
  def decode(uop: UInt): MultCtrl
  def fn(en: Bool, kill: Bool, src1: UInt, src2: UInt, a_signed: Bool, b_signed: Bool, high: Bool): (UInt, Bool, Bool)
}

object MultUtilsFactory extends UtilsFactory[MultUtils]("Mult")

object MultInit {
  val rv32iUtils  = riscv.RV32IMultUtils
  val rv32imUtils = riscv.RV32IMMultUtils
}
