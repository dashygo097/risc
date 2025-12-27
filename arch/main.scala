package arch

import core.alu._
import core.regfile._
import configs._
import utils._

object ALUTest extends App {
  ALUInit
  VerilogEmitter.parse(new ALU, s"${p(ISA)}_alu.sv")
}

object RegfileTest extends App {
  RegfileInit
  VerilogEmitter.parse(new Regfile, s"${p(ISA)}_regfile.sv")
}
