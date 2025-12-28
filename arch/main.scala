package arch

import core._
import core.decoder._
import core.regfile._
import core.alu._
import core.lsu._
import core.imm._
import configs._
import utils._

object Main extends App {
  DecoderInit
  RegfileInit
  ALUInit
  LSUInit
  ImmInit
  VerilogEmitter.parse(new RiscCore, s"${p(ISA)}_cpu.sv", lowering = true)
}

object DecoderTest extends App {
  DecoderInit
  VerilogEmitter.parse(new Decoder, s"${p(ISA)}_decoder.sv")
}

object RegfileTest extends App {
  RegfileInit
  VerilogEmitter.parse(new Regfile, s"${p(ISA)}_regfile.sv")
}

object ALUTest extends App {
  ALUInit
  VerilogEmitter.parse(new ALU, s"${p(ISA)}_alu.sv")
}
