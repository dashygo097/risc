package arch.core.bru

import arch.configs._
import chisel3._

trait BruUtilities extends Utilities {
  def branchTypeWidth: Int
  def hasJump: Boolean
  def hasJalr: Boolean
  def isJump(brType: UInt): Bool
  def isJalr(brType: UInt): Bool

  def fn(src1: UInt, src2: UInt, brType: UInt): Bool
}

object BruUtilitiesFactory extends UtilitiesFactory[BruUtilities]("BRU")

object BruInit {
  val rv32iUtils = RV32IBruUtilities
}
