package arch.core.decoder

import arch.configs._
import chisel3._

class Decoder(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_decoder"

  val utils = DecoderUtilitiesFactory.get(p(ISA)) match {
    case Some(u) => u
    case None    => throw new Exception(s"Decoder utilities for ISA ${p(ISA)} not found!")
  }

  val instr   = IO(Input(UInt(p(ILen).W)))
  val decoded = IO(Output(utils.createBundle()))

  decoded := utils.decode(instr)
}

object Decoder {
  def apply(instr: UInt)(implicit p: Parameters): Bundle = {
    val decoder = Module(new Decoder())
    decoder.instr := instr
    decoder.decoded
  }
}
