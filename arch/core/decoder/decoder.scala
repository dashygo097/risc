package arch.core.decoder

import arch.configs._
import chisel3._

class Decoder(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_decoder"

  val utils = DecoderUtilitiesFactory.getOrThrow(p(ISA))

  val instr   = IO(Input(UInt(p(ILen).W)))
  val decoded = IO(Output(new DecodedOutput))

  decoded := utils.decode(instr)
}

object Decoder {
  def apply(instr: UInt)(implicit p: Parameters): Bundle = {
    val decoder = Module(new Decoder())
    decoder.instr := instr
    decoder.decoded
  }
}
