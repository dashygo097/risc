package arch.core.imm

import chisel3._

trait ImmUtilities {
  def immTypeWidth: Int
  def createBundle: UInt
  def genImm(instr: UInt, immType: UInt): UInt
}
