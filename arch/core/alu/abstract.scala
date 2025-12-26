package arch.core.alu

import chisel3._

trait ALUUtilities {
  def fnTypeWidth: Int
  def fn(src1: UInt, src2: UInt, fnType: UInt, mode: Bool): UInt
}
