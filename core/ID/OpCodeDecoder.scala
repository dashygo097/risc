package core.id

import core.common._
import chisel3._

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
  val reg_write = IO(Output(Bool())).suggestName("REG_WRITE")
  val mem_read  = IO(Output(Bool())).suggestName("MEM_READ")
  val mem_write = IO(Output(Bool())).suggestName("MEM_WRITE")

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
}
