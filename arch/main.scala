package arch

import system._
import system.bridge._
import core._
import core.decoder._
import core.regfile._
import core.alu._
import core.lsu._
import core.imm._
import configs._
import utils._

object MainSystem extends App {
  DecoderInit
  RegfileInit
  AluInit
  LsuInit
  ImmInit

  BusBridgeInit
  VerilogEmitter.parse(new RiscSystem, s"${p(ISA)}_system.sv", lowering = true)
}

object MainCore extends App {
  DecoderInit
  RegfileInit
  AluInit
  LsuInit
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

object AluTest extends App {
  AluInit
  VerilogEmitter.parse(new Alu, s"${p(ISA)}_alu.sv")
}
