package core.id

import chisel3._

class RV32Funct3Decoder extends Module {
  override def desiredName: String = "rv32_funct3_decoder"

  val opcode = IO(Input(UInt(7.W))).suggestName("OPCODE")
  val funct3 = IO(Input(UInt(3.W))).suggestName("FUNCT3")

  val alu_op    = IO(Output(UInt(3.W))).suggestName("ALU_OP")
  val branch_op = IO(Output(UInt(3.W))).suggestName("BRANCH_OP")
  val mem_op    = IO(Output(UInt(3.W))).suggestName("MEM_OP")

  val mem_width    = IO(Output(UInt(2.W))).suggestName("MEM_WIDTH") // 0: byte, 1: half, 2: word
  val mem_sign_ext =
    IO(Output(Bool())).suggestName("MEM_SING_EXT") // 0: zero extend, 1: sign extend

  // I-type ALU
  alu_op := funct3

  // Branch
  branch_op := funct3

  // Load/Store
  mem_op := funct3

  // Memory width
  mem_width := funct3(1, 0)

  // Memory sign extension
  mem_sign_ext := !funct3(2)
}
