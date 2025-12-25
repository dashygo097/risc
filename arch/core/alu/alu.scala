package arch.core.alu

import arch._
import arch.configs._
import utils._
import chisel3._

class ALU(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_alu"

  val utils = ALUUtilitiesFactory.get(p(ISA)) match {
    case Some(u) => u
    case None    => throw new Exception(s"ALU utilities for ISA ${p(ISA)} not found!")
  }

}

// Test
object ALUTest extends App {
  ALUInit

  implicit val p: Parameters = Parameters.empty ++ Map(
    ISA  -> "rv32i",
    ILen -> 32,
    XLen -> 32
  )

  VerilogEmitter.parse(new ALU, s"alu.sv")

  println(s"✓ Verilog generated at: build/alu.sv")
}
