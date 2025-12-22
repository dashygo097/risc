package core.ex

import core.common._
import chisel3._
import chisel3.util._

class RV32ALU extends Module {
  override def desiredName: String = s"rv32_alu"

  val rs1 = IO(Input(UInt(32.W))).suggestName("ALU_RS1")
  val rs2 = IO(Input(UInt(32.W))).suggestName("ALU_RS2")
  val rd  = IO(Output(UInt(32.W))).suggestName("ALU_RD")

  // control signals
  val is_alu_sub = IO(Input(Bool())).suggestName("IS_ALU_SUB")
  val is_alu_sra = IO(Input(Bool())).suggestName("IS_ALU_SRA")
  val alu_op     = IO(Input(UInt(3.W))).suggestName("ALU_OP")

  rd := MuxLookup(alu_op, 0.U)(
    Seq(
      ALUOp.ADD  -> Mux(is_alu_sub, rs1 - rs2, rs1 + rs2),
      ALUOp.SLL  -> (rs1 << rs2(4, 0)),
      ALUOp.SLT  -> Mux(rs1.asSInt < rs2.asSInt, 1.U, 0.U),
      ALUOp.SLTU -> Mux(rs1 < rs2, 1.U, 0.U),
      ALUOp.XOR  -> (rs1 ^ rs2),
      ALUOp.SRL  -> Mux(is_alu_sra, (rs1.asSInt >> rs2(4, 0)).asUInt, rs1 >> rs2(4, 0)),
      ALUOp.OR   -> (rs1 | rs2),
      ALUOp.AND  -> (rs1 & rs2),
    )
  )
}
