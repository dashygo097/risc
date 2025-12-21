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

  // inst type signals
  val is_r_type = IO(Output(Bool())).suggestName("IS_R_TYPE")
  val is_i_type = IO(Output(Bool())).suggestName("IS_I_TYPE")
  val is_s_type = IO(Output(Bool())).suggestName("IS_S_TYPE")
  val is_b_type = IO(Output(Bool())).suggestName("IS_B_TYPE")
  val is_u_type = IO(Output(Bool())).suggestName("IS_U_TYPE")
  val is_j_type = IO(Output(Bool())).suggestName("IS_J_TYPE")

  // operation type signals
  val is_alu     = IO(Output(Bool())).suggestName("IS_ALU")
  val is_alu_imm = IO(Output(Bool())).suggestName("IS_ALU_IMM")
  val is_load    = IO(Output(Bool())).suggestName("IS_LOAD")
  val is_store   = IO(Output(Bool())).suggestName("IS_STORE")
  val is_branch  = IO(Output(Bool())).suggestName("IS_BRANCH")
  val is_jal     = IO(Output(Bool())).suggestName("IS_JAL")
  val is_jalr    = IO(Output(Bool())).suggestName("IS_JALR")
  val is_lui     = IO(Output(Bool())).suggestName("IS_LUI")
  val is_auipc   = IO(Output(Bool())).suggestName("IS_AUIPC")
  val is_system  = IO(Output(Bool())).suggestName("IS_SYSTEM")
  val is_fence   = IO(Output(Bool())).suggestName("IS_FENCE")

  // control signals
  val alu_ctrl = Output(UInt(4.W))

  val mem_ctrl     = Output(UInt(3.W))
  val mem_width    = Output(UInt(2.W))
  val mem_sign_ext = Output(Bool())

  val branch_op = Output(UInt(3.W))

  val reg_write = Output(Bool())
  val mem_read  = Output(Bool())
  val mem_write = Output(Bool())
  val alu_src   = Output(Bool())
  val pc_src    = Output(Bool())

  // Decode instruction segments
  opcode := inst(6, 0)
  rd     := inst(11, 7)
  funct3 := inst(14, 12)
  rs1    := inst(19, 15)
  rs2    := inst(24, 20)
  funct7 := inst(31, 25)

  // Modules
  val opcode_decoder = Module(new RV32OpCodeDecoder)
  val funct3_decoder = Module(new RV32Funct3Decoder)
  val funct7_decoder = Module(new RV32Funct7Decoder)

  // opcode decoder
  opcode_decoder.opcode := opcode

  reg_write := opcode_decoder.reg_write
  mem_read  := opcode_decoder.mem_read
  mem_write := opcode_decoder.mem_write
  alu_src   := opcode_decoder.alu_src
  pc_src    := opcode_decoder.pc_src

  is_r_type := opcode_decoder.is_r_type
  is_i_type := opcode_decoder.is_i_type
  is_s_type := opcode_decoder.is_s_type
  is_b_type := opcode_decoder.is_b_type
  is_u_type := opcode_decoder.is_u_type
  is_j_type := opcode_decoder.is_j_type

  is_alu     := opcode_decoder.is_alu
  is_alu_imm := opcode_decoder.is_alu_imm
  is_load    := opcode_decoder.is_load
  is_store   := opcode_decoder.is_store
  is_branch  := opcode_decoder.is_branch
  is_jal     := opcode_decoder.is_jal
  is_jalr    := opcode_decoder.is_jalr
  is_lui     := opcode_decoder.is_lui
  is_auipc   := opcode_decoder.is_auipc
  is_system  := opcode_decoder.is_system

  // funct3 decoder
  funct3_decoder.opcode := opcode
  funct3_decoder.funct3 := funct3
  funct3_decoder.funct7 := funct7

  branch_op    := funct3_decoder.branch_op
  mem_ctrl     := funct3_decoder.mem_op
  mem_width    := funct3_decoder.mem_width
  mem_sign_ext := funct3_decoder.mem_sign_ext

  // funct7 decoder
  funct7_decoder.opcode := opcode
  funct7_decoder.funct3 := funct3
  funct7_decoder.funct7 := funct7

  // ALU control signal
  alu_ctrl := MuxCase(
    ALUOp.ADD,
    Seq(
      is_alu     -> funct7_decoder.alu_op_r,   // R-type
      is_alu_imm -> funct3_decoder.alu_op_imm, // I-type
      is_lui     -> ALUOp.COPY,                // LUI
      is_auipc   -> ALUOp.ADD,                 // AUIPC
      is_load    -> ALUOp.ADD,                 // Load
      is_store   -> ALUOp.ADD,                 // Store
      is_branch  -> ALUOp.ADD,                 // Branch
      is_jal     -> ALUOp.ADD,                 // JAL
      is_jalr    -> ALUOp.ADD                  // JALR
    )
  )
}
