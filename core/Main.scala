package core

import utils._

object RV32_CPU_Main extends App {
  VerilogEmitter.parse(new RV32CPU, "rv32_cpu.sv", info = true, lowering = true)
}
