package core.id

import core.common._
import chisel3._
import chisel3.util._

class RV32OpCodeDecoder extends Module {
  override def desiredName: String = "rv32_opcode_decoder"

  val opcode = IO(Input(UInt(7.W))).suggestName("OPCODE")

  // inst type signals
  val is_r_type = IO(Output(Bool())).suggestName("IS_R_TYPE")
  val is_i_type = IO(Output(Bool())).suggestName("IS_I_TYPE")
  val is_s_type = IO(Output(Bool())).suggestName("IS_S_TYPE")
  val is_b_type = IO(Output(Bool())).suggestName("IS_B_TYPE")
  val is_u_type = IO(Output(Bool())).suggestName("IS_U_TYPE")
  val is_j_type = IO(Output(Bool())).suggestName("IS_J_TYPE")

  // operation type signals
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

  // control signals
  val reg_write   = IO(Output(Bool())).suggestName("REG_WRITE")
  val mem_read    = IO(Output(Bool())).suggestName("MEM_READ")
  val mem_write   = IO(Output(Bool())).suggestName("MEM_WRITE")
  val alu_rs1_sel = IO(Output(UInt(2.W))).suggestName("ALU_RS1_SEL") // [0]: 0: 0, [1]: rs1, [2]: PC
  val alu_rs2_sel =
    IO(Output(UInt(2.W))).suggestName("ALU_RS2_SEL") // [0]: 0, [1]: rs2, [2]: imm, [3]: 4

  is_r_type := opcode === OpCode.OP
  is_i_type := (opcode === OpCode.OP_IMM) ||
    (opcode === OpCode.LOAD) ||
    (opcode === OpCode.JALR) ||
    (opcode === OpCode.SYSTEM)
  is_s_type := opcode === OpCode.STORE
  is_b_type := opcode === OpCode.BRANCH
  is_u_type := (opcode === OpCode.LUI) || (opcode === OpCode.AUIPC)
  is_j_type := opcode === OpCode.JAL

  is_op     := opcode === OpCode.OP
  is_op_imm := opcode === OpCode.OP_IMM
  is_load   := opcode === OpCode.LOAD
  is_store  := opcode === OpCode.STORE
  is_branch := opcode === OpCode.BRANCH
  is_jal    := opcode === OpCode.JAL
  is_jalr   := opcode === OpCode.JALR
  is_lui    := opcode === OpCode.LUI
  is_auipc  := opcode === OpCode.AUIPC
  is_system := opcode === OpCode.SYSTEM

  reg_write := is_r_type || is_i_type || is_u_type ||
    is_j_type || is_load

  mem_read  := is_load
  mem_write := is_store

  alu_rs1_sel := MuxLookup(opcode, 0.U(2.W))(
    Seq(
      OpCode.LUI    -> 0.U, // 0
      OpCode.OP     -> 1.U, // rs1
      OpCode.OP_IMM -> 1.U, // rs1
      OpCode.LOAD   -> 1.U, // rs1
      OpCode.STORE  -> 1.U, // rs1
      OpCode.BRANCH -> 1.U, // rs1
      OpCode.JALR   -> 1.U, // rs1
      OpCode.AUIPC  -> 2.U, // PC
      OpCode.JAL    -> 2.U, // PC
    )
  )

  alu_rs2_sel := MuxLookup(opcode, 0.U(2.W))(
    Seq(
      OpCode.OP     -> 1.U, // rs2
      OpCode.OP_IMM -> 2.U, // imm
      OpCode.LOAD   -> 2.U, // imm
      OpCode.STORE  -> 2.U, // imm
      OpCode.BRANCH -> 2.U, // imm
      OpCode.LUI    -> 2.U, // imm
      OpCode.AUIPC  -> 2.U, // imm
      OpCode.JAL    -> 3.U, // 4
      OpCode.JALR   -> 3.U  // 4
    )
  )

}
