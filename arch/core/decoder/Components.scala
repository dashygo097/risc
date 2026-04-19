package arch.core.decoder

import arch.configs._
import chisel3._
import chisel3.util.BitPat

trait DecoderUtils extends Utils {
  def default: List[BitPat]
  def decode(instr: UInt): DecodedOutput
  def table: Array[(BitPat, List[BitPat])]
}

object DecoderUtilsFactory extends UtilsFactory[DecoderUtils]("Decoder")

object DecoderInit {
  val rv32iUtils  = riscv.RV32IDecoderUtils
  val rv32imUtils = riscv.RV32IMDecoderUtils
}
