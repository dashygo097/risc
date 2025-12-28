package core

import utils._

object Main extends App {
  VerilogEmitter.parse(new RV32CPU, "rv32i_cpu.sv", info = true, lowering = true)
}
