package arch.core.lsu

import chisel3._

trait LsuUtilities {
  def cmdWidth: Int
  def strb(cmd: UInt): UInt

  def isByte(cmd: UInt): Bool
  def isHalf(cmd: UInt): Bool
  def isWord(cmd: UInt): Bool
  def isUnsigned(cmd: UInt): Bool
  def isRead(cmd: UInt): Bool
  def isWrite(cmd: UInt): Bool
  def isMemRead(is_mem: Bool, cmd: UInt): Bool
  def isMemWrite(is_mem: Bool, cmd: UInt): Bool
}
