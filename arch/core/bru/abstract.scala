package arch.core.bru

import chisel3._

trait BruUtilities {
  def hasJump: Boolean
  def hasJalr: Boolean
  def branchTypeWidth: Int
  def isJump(brType: UInt): Bool
  def isJalr(brType: UInt): Bool
  def fn(src1: UInt, src2: UInt, fnType: UInt): Bool
}
