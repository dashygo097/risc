package arch.core.decoder

import chisel3._
import chisel3.util.BitPat

trait DecoderUtilities {
  def default: List[BitPat]
  def createBundle(): Bundle
  def decode(instr: UInt): Bundle
  def table: Array[(BitPat, List[BitPat])]
}
