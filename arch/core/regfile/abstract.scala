package arch.core.regfile

import utils.Register
import chisel3._

trait RegfileUtilities {
  def width: Int
  def getRs1(instr: UInt): UInt
  def getRs2(instr: UInt): UInt
  def getRd(instr: UInt): UInt
  def extraInfo: Seq[Register]
}
