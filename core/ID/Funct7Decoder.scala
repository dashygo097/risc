package core.id

import core.common._
import chisel3._
import chisel3.util._

class RV32Funct7Decoder extends Module {
  override def desiredName: String = "rv32_funct7_decoder"

  val opcode = IO(Input(UInt(7.W))).suggestName("OPCODE")
  val funct3 = IO(Input(UInt(3.W))).suggestName("FUNCT3")
  val funct7 = IO(Input(UInt(7.W))).suggestName("FUNCT7")

  // R-Type ALU
  val alu_op_r = IO(Output(UInt(4.W))).suggestName("ALU_OP_R")

  // Determine if the instruction is SUB or SRA
  val is_alu_sub = IO(Output(Bool())).suggestName("IS_ALU_SUB")
  val is_alu_sra = IO(Output(Bool())).suggestName("IS_ALU_SRA")

  // Base ALU operation based on funct3
  val alu_op_base = MuxLookup(funct3, ALUOp.ADD)(
    Seq(
      "b000".U -> ALUOp.ADD,  // ADD/SUB
      "b001".U -> ALUOp.SLL,  // SLL
      "b010".U -> ALUOp.SLT,  // SLT
      "b011".U -> ALUOp.SLTU, // SLTU
      "b100".U -> ALUOp.XOR,  // XOR
      "b101".U -> ALUOp.SRL,  // SRL/SRA
      "b110".U -> ALUOp.OR,   // OR
      "b111".U -> ALUOp.AND   // AND
    )
  )

  // Identify specific instructions
  is_alu_sub := (funct3 === "b000".U) && (funct7 === "b0100000".U)
  is_alu_sra := (funct3 === "b101".U) && (funct7 === "b0100000".U)

  alu_op_r := MuxCase(
    alu_op_base,
    Seq(
      is_alu_sub -> ALUOp.SUB,
      is_alu_sra -> ALUOp.SRA
    )
  )
}
