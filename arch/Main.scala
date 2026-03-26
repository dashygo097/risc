package arch

import system._
import system.bridge._
import system.crossbar._
import core._
import core.decoder._
import core.bru._
import core.regfile._
import core.alu._
import core.mul._
import core.lsu._
import core.imm._
import core.csr._
import configs._
import vopts.utils._

object MainCore extends App {
  DecoderInit
  BruInit
  RegfileInit
  AluInit
  MulInit
  LsuInit
  ImmInit
  CsrInit

  VerilogEmitter.parse(new RiscCore, s"${p(ISA)}_cpu.sv", lowering = true)
  RiscDump.dump(
    p = p,
    configPath = "build/config.json",
    isaPath = "build/isa.json",
    binPath = Some("build/config.pb"),
    isaBinPath = Some("build/isa.pb"),
  )
}

object MainSystem extends App {
  DecoderInit
  BruInit
  RegfileInit
  AluInit
  MulInit
  LsuInit
  ImmInit
  CsrInit

  BusBridgeInit
  BusCrossbarInit

  VerilogEmitter.parse(new RiscSystem, s"${p(ISA)}_system.sv", lowering = true)
  RiscDump.dump(
    p = p,
    configPath = "build/config.json",
    isaPath = "build/isa.json",
    binPath = Some("build/config.pb"),
    isaBinPath = Some("build/isa.pb"),
  )
}
