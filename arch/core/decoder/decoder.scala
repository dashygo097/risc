package arch.core.decoder

import arch.configs._
import utils._
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

// Test
object DecoderTest extends App {
  DecoderInit
  VerilogEmitter.parse(new Decoder, s"${p(ISA)}_decoder.sv")
  println(s"✓ Verilog generated at: build/${p(ISA)}_decoder.sv")
}
