package arch

import core.alu._
import configs._
import utils._

object ALUTest extends App {
  ALUInit
  VerilogEmitter.parse(new ALU, s"${p(ISA)}_alu.sv")
}
