package core

import chisel3._

class RV32CPU extends Module {
  override def desiredName: String =
    s"rv32_cpu"

  val inst = IO(Input(UInt(32.W)))

  // Modules
  val alu = Module(new RV32ALU)
  val globl_ctrl = Module(new RV32GloblCtrlUnit)
  val regfile = Module(new RV32RegFile)

  val temp_mem = Module(new RV32GloblMem(32, 32, 64, 0x0L))

  // Connections
  
  // Program Counter
  val pc = RegInit(0.U(32.W))
  val nextPC = Wire(UInt(32.W))
}
