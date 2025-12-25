package arch.core.decoder

import arch._
import arch.configs._
import utils._
import chisel3._

class Decoder(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_decoder"

  val table    = DecodeTableFactory.get(p(ISA)) match {
    case Some(t) => t
    case None    => throw new Exception(s"DecodeTable for ISA ${p(ISA)} not found!")
  }
  val sigsType = DecodeCtrlSigsFactory.get(p(ISA)) match {
    case Some(s) => s
    case None    => throw new Exception(s"DecodeCtrlSigs for ISA ${p(ISA)} not found!")
  }

  val instr   = IO(Input(UInt(p(ILen).W)))
  val decoded = IO(Output(sigsType))

  decoded := Wire(sigsType.decode(instr, table.table))
}

// Test
object DecoderInit {
  val rv32iCtrlSigs    = new RV32ICtrlSigs
  val rv32iDecodeTable = new RV32IDecodeTable
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
