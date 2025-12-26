package arch.core.imm

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
  VerilogEmitter.parse(new ImmGen, s"${p(ISA)}_immgen.sv")
  println(s"✓ Verilog generated at: build/${p(ISA)}_immgen.sv")
}
