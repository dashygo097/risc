package arch.core.lsu

import arch._
import arch.configs._
import utils._
import chisel3._

class LSU(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_lsu"

  val utils = LSUUtilitiesFactory.get(p(ISA)) match {
    case Some(u) => u
    case None    => throw new Exception(s"LSU utilities for ISA ${p(ISA)} not found!")
  }

}

// Test
object LSUTest extends App {
  LSUInit

  implicit val p: Parameters = Parameters.empty ++ Map(
    ISA  -> "rv32i",
    ILen -> 32,
    XLen -> 32
  )

  VerilogEmitter.parse(new LSU, s"lsu.sv")

  println(s"✓ Verilog generated at: build/lsu.sv")
}
