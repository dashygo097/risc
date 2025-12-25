package arch.core.decoder

import chisel3._
import chisel3.util.BitPat

abstract trait DecodeTable {
  val table: Array[(BitPat, List[BitPat])]
}

abstract class DecodeCtrlSigs extends Bundle {
  def default: List[BitPat]
  def decode(instr: UInt, table: Iterable[(BitPat, List[BitPat])]): DecodeCtrlSigs;
}
