package core

import chisel3._

class ID_EX extends Module {
  override def desiredName: String = s"id_ex"
  val STALL                        = IO(Input(Bool()))
  val FLUSH                        = IO(Input(Bool()))

  // Control signals
  val ID_ALU_OP     = IO(Input(UInt(3.W)))
  val ID_ALU_IS_SUB = IO(Input(Bool()))
  val ID_ALU_IS_SRA = IO(Input(Bool()))
  val ID_MEM_OP     = IO(Input(UInt(3.W)))
  val ID_REG_WRITE  = IO(Input(Bool()))
  val ID_MEM_READ   = IO(Input(Bool()))
  val ID_MEM_WRITE  = IO(Input(Bool()))

  val ID_IS_OP     = IO(Input(Bool()))
  val ID_IS_OP_IMM = IO(Input(Bool()))
  val ID_IS_LOAD   = IO(Input(Bool()))
  val ID_IS_STORE  = IO(Input(Bool()))
  val ID_IS_BRANCH = IO(Input(Bool()))
  val ID_IS_JAL    = IO(Input(Bool()))
  val ID_IS_JALR   = IO(Input(Bool()))
  val ID_IS_LUI    = IO(Input(Bool()))
  val ID_IS_AUIPC  = IO(Input(Bool()))
  val ID_IS_SYSTEM = IO(Input(Bool()))

  // Data
  val ID_PC       = IO(Input(UInt(32.W)))
  val ID_INST     = IO(Input(UInt(32.W)))
  val ID_RS1_DATA = IO(Input(UInt(32.W)))
  val ID_RS2_DATA = IO(Input(UInt(32.W)))
  val ID_IMM      = IO(Input(UInt(32.W)))
  val ID_RD       = IO(Input(UInt(5.W)))
  val ID_RS1      = IO(Input(UInt(5.W)))
  val ID_RS2      = IO(Input(UInt(5.W)))
  val ID_FUNCT3   = IO(Input(UInt(3.W)))
  val ID_OPCODE   = IO(Input(UInt(7.W)))

  // Outputs to EX stage
  val EX_ALU_OP     = IO(Output(UInt(3.W)))
  val EX_ALU_IS_SUB = IO(Output(Bool()))
  val EX_ALU_IS_SRA = IO(Output(Bool()))
  val EX_MEM_OP     = IO(Output(UInt(3.W)))
  val EX_REG_WRITE  = IO(Output(Bool()))
  val EX_MEM_READ   = IO(Output(Bool()))
  val EX_MEM_WRITE  = IO(Output(Bool()))

  val EX_IS_OP     = IO(Output(Bool()))
  val EX_IS_OP_IMM = IO(Output(Bool()))
  val EX_IS_LOAD   = IO(Output(Bool()))
  val EX_IS_STORE  = IO(Output(Bool()))
  val EX_IS_BRANCH = IO(Output(Bool()))
  val EX_IS_JAL    = IO(Output(Bool()))
  val EX_IS_JALR   = IO(Output(Bool()))
  val EX_IS_LUI    = IO(Output(Bool()))
  val EX_IS_AUIPC  = IO(Output(Bool()))
  val EX_IS_SYSTEM = IO(Output(Bool()))

  val EX_PC       = IO(Output(UInt(32.W)))
  val EX_INST     = IO(Output(UInt(32.W)))
  val EX_RS1_DATA = IO(Output(UInt(32.W)))
  val EX_RS2_DATA = IO(Output(UInt(32.W)))
  val EX_IMM      = IO(Output(UInt(32.W)))
  val EX_RD       = IO(Output(UInt(5.W)))
  val EX_RS1      = IO(Output(UInt(5.W)))
  val EX_RS2      = IO(Output(UInt(5.W)))
  val EX_FUNCT3   = IO(Output(UInt(3.W)))
  val EX_OPCODE   = IO(Output(UInt(7.W)))

  // Registers
  val alu_op_reg     = RegInit(0.U(3.W))
  val alu_is_sub_reg = RegInit(false.B)
  val alu_is_sra_reg = RegInit(false.B)
  val mem_op_reg     = RegInit(0.U(3.W))
  val reg_write_reg  = RegInit(false.B)
  val mem_read_reg   = RegInit(false.B)
  val mem_write_reg  = RegInit(false.B)

  val is_op_reg     = RegInit(false.B)
  val is_op_imm_reg = RegInit(false.B)
  val is_load_reg   = RegInit(false.B)
  val is_store_reg  = RegInit(false.B)
  val is_branch_reg = RegInit(false.B)
  val is_jal_reg    = RegInit(false.B)
  val is_jalr_reg   = RegInit(false.B)
  val is_lui_reg    = RegInit(false.B)
  val is_auipc_reg  = RegInit(false.B)
  val is_system_reg = RegInit(false.B)

  val pc_reg       = RegInit(0.U(32.W))
  val inst_reg     = RegInit(0.U(32.W))
  val rs1_data_reg = RegInit(0.U(32.W))
  val rs2_data_reg = RegInit(0.U(32.W))
  val imm_reg      = RegInit(0.U(32.W))
  val rd_reg       = RegInit(0.U(5.W))
  val rs1_reg      = RegInit(0.U(5.W))
  val rs2_reg      = RegInit(0.U(5.W))
  val funct3_reg   = RegInit(0.U(3.W))
  val opcode_reg   = RegInit(0.U(7.W))

  when(FLUSH) {
    alu_op_reg     := 0.U
    alu_is_sub_reg := false.B
    alu_is_sra_reg := false.B
    mem_op_reg     := 0.U
    reg_write_reg  := false.B
    mem_read_reg   := false.B
    mem_write_reg  := false.B

    is_op_reg     := false.B
    is_op_imm_reg := false.B
    is_load_reg   := false.B
    is_store_reg  := false.B
    is_branch_reg := false.B
    is_jal_reg    := false.B
    is_jalr_reg   := false.B
    is_lui_reg    := false.B
    is_auipc_reg  := false.B
    is_system_reg := false.B

    pc_reg       := 0.U
    inst_reg     := 0.U
    rs1_data_reg := 0.U
    rs2_data_reg := 0.U
    imm_reg      := 0.U
    rd_reg       := 0.U
    rs1_reg      := 0.U
    rs2_reg      := 0.U
    funct3_reg   := 0.U
    opcode_reg   := 0.U

  }.elsewhen(!STALL) {
    alu_op_reg     := ID_ALU_OP
    alu_is_sub_reg := ID_ALU_IS_SUB
    alu_is_sra_reg := ID_ALU_IS_SRA
    mem_op_reg     := ID_MEM_OP
    reg_write_reg  := ID_REG_WRITE
    mem_read_reg   := ID_MEM_READ
    mem_write_reg  := ID_MEM_WRITE

    is_op_reg     := ID_IS_OP
    is_op_imm_reg := ID_IS_OP_IMM
    is_load_reg   := ID_IS_LOAD
    is_store_reg  := ID_IS_STORE
    is_branch_reg := ID_IS_BRANCH
    is_jal_reg    := ID_IS_JAL
    is_jalr_reg   := ID_IS_JALR
    is_lui_reg    := ID_IS_LUI
    is_auipc_reg  := ID_IS_AUIPC
    is_system_reg := ID_IS_SYSTEM

    pc_reg       := ID_PC
    inst_reg     := ID_INST
    rs1_data_reg := ID_RS1_DATA
    rs2_data_reg := ID_RS2_DATA
    imm_reg      := ID_IMM
    rd_reg       := ID_RD
    rs1_reg      := ID_RS1
    rs2_reg      := ID_RS2
    funct3_reg   := ID_FUNCT3
    opcode_reg   := ID_OPCODE
  }

  EX_ALU_OP     := alu_op_reg
  EX_ALU_IS_SUB := alu_is_sub_reg
  EX_ALU_IS_SRA := alu_is_sra_reg
  EX_MEM_OP     := mem_op_reg
  EX_REG_WRITE  := reg_write_reg
  EX_MEM_READ   := mem_read_reg
  EX_MEM_WRITE  := mem_write_reg

  EX_IS_OP     := is_op_reg
  EX_IS_OP_IMM := is_op_imm_reg
  EX_IS_LOAD   := is_load_reg
  EX_IS_STORE  := is_store_reg
  EX_IS_BRANCH := is_branch_reg
  EX_IS_JAL    := is_jal_reg
  EX_IS_JALR   := is_jalr_reg
  EX_IS_LUI    := is_lui_reg
  EX_IS_AUIPC  := is_auipc_reg
  EX_IS_SYSTEM := is_system_reg

  EX_PC       := pc_reg
  EX_INST     := inst_reg
  EX_RS1_DATA := rs1_data_reg
  EX_RS2_DATA := rs2_data_reg
  EX_IMM      := imm_reg
  EX_RD       := rd_reg
  EX_RS1      := rs1_reg
  EX_RS2      := rs2_reg
  EX_FUNCT3   := funct3_reg
  EX_OPCODE   := opcode_reg
}
