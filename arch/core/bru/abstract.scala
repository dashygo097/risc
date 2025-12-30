package arch.core.bru

import chisel3._

trait BruUtilities {
  def branchTypeWidth: Int
  def fn(src1: UInt, src2: UInt, fnType: UInt): Bool
}
