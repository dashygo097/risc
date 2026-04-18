package arch.core.bru

import arch.configs._
import chisel3._

trait BruUtilities extends Utilities {
  def opWidth: Int
  def hasJump: Boolean
  def hasJalr: Boolean

  def decode(uop: UInt): BruCtrl
  def fn(src1: UInt, src2: UInt, op: UInt): Bool
}

object BruUtilitiesFactory extends UtilitiesFactory[BruUtilities]("BRU")

object BruInit {
  val rv32iUtils  = RV32IBruUtilities
  val rv32imUtils = RV32IMBruUtilities
}
