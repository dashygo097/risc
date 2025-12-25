package arch.core.decoder

import arch._
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

  decoded := utils.decode(instr, utils.table)
}

// Test
object DecoderInit {
  val rv32iUtils = RV32IDecoderUtilities
}

object DecoderTest extends App {
  DecoderInit

  implicit val p: Parameters = Parameters.empty ++ Map(
    ISA  -> "rv32i",
    ILen -> 32,
    XLen -> 32
  )

  VerilogEmitter.parse(new Decoder, s"decoder.sv")

  println(s"✓ Verilog generated at: build/decoder.sv")
}
