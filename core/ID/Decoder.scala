package core.id

import core.common._
import chisel3._
import chisel3.util._

class RV32Decoder extends Module {
  override def desiredName: String = "rv32_decoder"

  // Input: 32-bit instruction
  val inst = IO(Input(UInt(32.W))).suggestName("INST")

  // inst segments
  val opcode = IO(Output(UInt(7.W))).suggestName("OPCODE")
  val rd     = IO(Output(UInt(5.W))).suggestName("RD")
  val funct3 = IO(Output(UInt(3.W))).suggestName("FUNCT3")
  val rs1    = IO(Output(UInt(5.W))).suggestName("RS1")
  val rs2    = IO(Output(UInt(5.W))).suggestName("RS2")
  val funct7 = IO(Output(UInt(7.W))).suggestName("FUNCT7")

  // opcode
  val is_r_type = IO(Output(Bool())).suggestName("IS_R_TYPE")
  val is_i_type = IO(Output(Bool())).suggestName("IS_I_TYPE")
  val is_s_type = IO(Output(Bool())).suggestName("IS_S_TYPE")
  val is_b_type = IO(Output(Bool())).suggestName("IS_B_TYPE")
  val is_u_type = IO(Output(Bool())).suggestName("IS_U_TYPE")
  val is_j_type = IO(Output(Bool())).suggestName("IS_J_TYPE")

  val is_op     = IO(Output(Bool())).suggestName("IS_OP")
  val is_op_imm = IO(Output(Bool())).suggestName("IS_OP_IMM")
  val is_load   = IO(Output(Bool())).suggestName("IS_LOAD")
  val is_store  = IO(Output(Bool())).suggestName("IS_STORE")
  val is_branch = IO(Output(Bool())).suggestName("IS_BRANCH")
  val is_jal    = IO(Output(Bool())).suggestName("IS_JAL")
  val is_jalr   = IO(Output(Bool())).suggestName("IS_JALR")
  val is_lui    = IO(Output(Bool())).suggestName("IS_LUI")
  val is_auipc  = IO(Output(Bool())).suggestName("IS_AUIPC")
  val is_system = IO(Output(Bool())).suggestName("IS_SYSTEM")

  val alu_rs1_sel = IO(Output(UInt(2.W))).suggestName("ALU_RS1_SEL")
  val alu_rs2_sel = IO(Output(UInt(2.W))).suggestName("ALU_RS2_SEL")
  val reg_write   = IO(Output(Bool())).suggestName("REG_WRITE")
  val mem_read    = IO(Output(Bool())).suggestName("MEM_READ")
  val mem_write   = IO(Output(Bool())).suggestName("MEM_WRITE")

  // funct3
  val alu_op    = IO(Output(UInt(3.W))).suggestName("ALU_OP")
  val branch_op = IO(Output(UInt(3.W))).suggestName("BRANCH_OP")
  val mem_op    = IO(Output(UInt(3.W))).suggestName("MEM_OP")

  // funct7
  val alu_is_sub = IO(Output(Bool())).suggestName("ALU_IS_SUB")
  val alu_is_sra = IO(Output(Bool())).suggestName("ALU_IS_SRA")

  // Decode instruction segments
  opcode := inst(6, 0)
  rd     := inst(11, 7)
  funct3 := inst(14, 12)
  rs1    := inst(19, 15)
  rs2    := inst(24, 20)
  funct7 := inst(31, 25)

  // Modules
  val opcode_decoder = Module(new RV32OpCodeDecoder)

  // opcode decoder
  opcode_decoder.opcode := opcode

  is_r_type := opcode_decoder.is_r_type
  is_i_type := opcode_decoder.is_i_type
  is_s_type := opcode_decoder.is_s_type
  is_b_type := opcode_decoder.is_b_type
  is_u_type := opcode_decoder.is_u_type
  is_j_type := opcode_decoder.is_j_type

  is_op     := opcode_decoder.is_op
  is_op_imm := opcode_decoder.is_op_imm
  is_load   := opcode_decoder.is_load
  is_store  := opcode_decoder.is_store
  is_branch := opcode_decoder.is_branch
  is_jal    := opcode_decoder.is_jal
  is_jalr   := opcode_decoder.is_jalr
  is_lui    := opcode_decoder.is_lui
  is_auipc  := opcode_decoder.is_auipc
  is_system := opcode_decoder.is_system

  alu_rs1_sel := opcode_decoder.alu_rs1_sel
  alu_rs2_sel := opcode_decoder.alu_rs2_sel
  reg_write   := opcode_decoder.reg_write
  mem_read    := opcode_decoder.mem_read
  mem_write   := opcode_decoder.mem_write

  // funct3 decoder
  alu_op    := MuxCase(
    ALUOp.ADD,
    Seq(
      is_op     -> funct3,
      is_op_imm -> funct3,
      is_load   -> ALUOp.ADD,
      is_store  -> ALUOp.ADD,
      is_lui    -> ALUOp.ADD,
      is_auipc  -> ALUOp.ADD,
      is_jal    -> ALUOp.ADD,
      is_jalr   -> ALUOp.ADD
    )
  )
  branch_op := funct3
  mem_op    := funct3

  // funct7 decoder
  alu_is_sub := (funct3 === ALUOp.ADD) && (funct7 === "b0100000".U)
  alu_is_sra := (funct3 === ALUOp.SRL) && (funct7 === "b0100000".U)
}
