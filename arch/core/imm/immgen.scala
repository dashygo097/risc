package arch.core.imm

import arch._
import arch.configs._
import utils._
import chisel3._

class ImmGen(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_immgen"

  val utils = ImmUtilitiesFactory.get(p(ISA)) match {
    case Some(u) => u
    case None    => throw new Exception(s"ImmGen utilities for ISA ${p(ISA)} not found!")
  }

  val instr   = IO(Input(UInt(p(ILen).W)))
  val immType = IO(Input(UInt(utils.immTypeWidth.W)))
  val imm     = IO(Output(utils.createBundle))

  imm := utils.genImm(instr, immType)
}

// Test
object ImmTest extends App {
  ImmInit

  implicit val p: Parameters = Parameters.empty ++ Map(
    ISA  -> "rv32i",
    ILen -> 32,
    XLen -> 32
  )

  VerilogEmitter.parse(new ImmGen, s"immgen.sv")

  println(s"✓ Verilog generated at: build/immgen.sv")
}
