package core

import chisel3._
import chisel3.util._
import utils._

object ALUOp {
  val ADD  = 0x0.U
  val SUB  = 0x1.U
  val SLL  = 0x2.U
  val SLT  = 0x3.U
  val SLTU = 0x4.U
  val XOR  = 0x5.U
  val SRL  = 0x6.U
  val SRA  = 0x7.U
  val OR   = 0x8.U
  val AND  = 0x9.U
}

class RV32ALU extends Module {
  override def desiredName: String = s"rv32_alu"

  val rs1      = IO(Input(UInt(32.W))).suggestName("ALU_RS1")
  val rs2      = IO(Input(UInt(32.W))).suggestName("ALU_RS2")
  val rd       = IO(Output(UInt(32.W))).suggestName("ALU_RD")
  val alu_ctrl = IO(Input(UInt(4.W))).suggestName("ALU_CTRL")

  rd := MuxLookup(alu_ctrl, 0.U)(
    Seq(
      ALUOp.ADD  -> (rs1 + rs2),
      ALUOp.SUB  -> (rs1 - rs2),
      ALUOp.SLL  -> (rs1 << rs2(4, 0)),
      ALUOp.SLT  -> Mux(rs1.asSInt < rs2.asSInt, 1.U, 0.U),
      ALUOp.SLTU -> Mux(rs1 < rs2, 1.U, 0.U),
      ALUOp.XOR  -> (rs1 ^ rs2),
      ALUOp.SRL  -> (rs1 >> rs2(4, 0)),
      ALUOp.SRA  -> (rs1.asSInt >> rs2(4, 0)).asUInt,
      ALUOp.OR   -> (rs1 | rs2),
      ALUOp.AND  -> (rs1 & rs2)
    )
  )
}

object RV32ALU extends App {
  VerilogEmitter.parse(new RV32ALU, "rv32_alu.sv", info = true)
}
