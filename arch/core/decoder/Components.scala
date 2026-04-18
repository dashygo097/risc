package arch.core.decoder

import arch.configs._
import chisel3._
import chisel3.util.BitPat

trait DecoderUtilities extends Utilities {
  def default: List[BitPat]
  def decode(instr: UInt): DecodedOutput
  def table: Array[(BitPat, List[BitPat])]
}

object DecoderUtilitiesFactory extends UtilitiesFactory[DecoderUtilities]("Decoder")

object DecoderInit {
  val rv32iUtils  = RV32IDecoderUtilities
  val rv32imUtils = RV32IMDecoderUtilities
}
