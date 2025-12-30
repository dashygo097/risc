package arch

import system._
import system.bridge._
import core._
import core.decoder._
import core.bru._
import core.regfile._
import core.alu._
import core.lsu._
import core.imm._
import configs._
import utils._

object MainSystem extends App {
  DecoderInit
  BruInit
  RegfileInit
  AluInit
  LsuInit
  ImmInit

  BusBridgeInit
  VerilogEmitter.parse(new RiscSystem, s"${p(ISA)}_system.sv", lowering = true)
}

object MainCore extends App {
  DecoderInit
  BruInit
  RegfileInit
  AluInit
  LsuInit
  ImmInit
  VerilogEmitter.parse(new RiscCore, s"${p(ISA)}_cpu.sv", lowering = true)
}
