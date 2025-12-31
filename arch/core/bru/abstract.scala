package arch.core.bru

import chisel3._

trait BruUtilities {
  def branchTypeWidth: Int
  def hasJalr: Boolean
  def isJalr(brType: UInt): Bool
  def fn(src1: UInt, src2: UInt, brType: UInt): Bool
}
