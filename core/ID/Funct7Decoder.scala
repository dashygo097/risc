package core.id

import core.common._
import chisel3._

class RV32Funct7Decoder extends Module {
  override def desiredName: String = "rv32_funct7_decoder"

  val opcode = IO(Input(UInt(7.W))).suggestName("OPCODE")
  val funct3 = IO(Input(UInt(3.W))).suggestName("FUNCT3")
  val funct7 = IO(Input(UInt(7.W))).suggestName("FUNCT7")

  // Determine if the instruction is SUB or SRA
  val alu_is_sub = IO(Output(Bool())).suggestName("IS_ALU_SUB")
  val alu_is_sra = IO(Output(Bool())).suggestName("IS_ALU_SRA")

  // Identify specific instructions
  alu_is_sub := (funct3 === ALUOp.ADD) && (funct7 === "b0100000".U)
  alu_is_sra := (funct3 === ALUOp.SRL) && (funct7 === "b0100000".U)
}
