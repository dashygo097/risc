package arch.core.bru

import arch.configs._
import chisel3._

trait BruUtils extends Utils {
  def opWidth: Int
  def hasJump: Boolean
  def hasJalr: Boolean

  def decode(uop: UInt): BruCtrl
  def fn(src1: UInt, src2: UInt, op: UInt): Bool
}

object BruUtilsFactory extends UtilsFactory[BruUtils]("BRU")

object BruInit {
  val rv32iUtils  = riscv.RV32IBruUtils
  val rv32imUtils = riscv.RV32IMBruUtils
}
