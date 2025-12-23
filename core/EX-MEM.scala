package core

import chisel3._

class EX_MEM extends Module {
  override def desiredName: String = s"ex_mem"

  val STALL = IO(Input(Bool()))
  val FLUSH = IO(Input(Bool()))

  // Control signals
  val EX_MEM_OP    = IO(Input(UInt(3.W)))
  val EX_REG_WRITE = IO(Input(Bool()))
  val EX_MEM_READ  = IO(Input(Bool()))
  val EX_MEM_WRITE = IO(Input(Bool()))

  val EX_IS_LOAD = IO(Input(Bool()))
  val EX_IS_LUI  = IO(Input(Bool()))
  val EX_IS_JAL  = IO(Input(Bool()))
  val EX_IS_JALR = IO(Input(Bool()))

  // Data
  val EX_ALU_RESULT = IO(Input(UInt(32.W)))
  val EX_RS2_DATA   = IO(Input(UInt(32.W)))
  val EX_RD         = IO(Input(UInt(5.W)))
  val EX_FUNCT3     = IO(Input(UInt(3.W)))
  val EX_PC         = IO(Input(UInt(32.W)))
  val EX_OPCODE     = IO(Input(UInt(7.W)))
  val EX_INST       = IO(Input(UInt(32.W)))
  val EX_IMM        = IO(Input(UInt(32.W)))

  // Outputs to MEM stage
  val MEM_MEM_OP    = IO(Output(UInt(3.W)))
  val MEM_REG_WRITE = IO(Output(Bool()))
  val MEM_MEM_READ  = IO(Output(Bool()))
  val MEM_MEM_WRITE = IO(Output(Bool()))

  val MEM_IS_LOAD = IO(Output(Bool()))
  val MEM_IS_LUI  = IO(Output(Bool()))
  val MEM_IS_JAL  = IO(Output(Bool()))
  val MEM_IS_JALR = IO(Output(Bool()))

  val MEM_ALU_RESULT = IO(Output(UInt(32.W)))
  val MEM_RS2_DATA   = IO(Output(UInt(32.W)))
  val MEM_RD         = IO(Output(UInt(5.W)))
  val MEM_FUNCT3     = IO(Output(UInt(3.W)))
  val MEM_PC         = IO(Output(UInt(32.W)))
  val MEM_OPCODE     = IO(Output(UInt(7.W)))
  val MEM_INST       = IO(Output(UInt(32.W)))
  val MEM_IMM        = IO(Output(UInt(32.W)))

  // Registers
  val mem_op_reg    = RegInit(0.U(3.W))
  val reg_write_reg = RegInit(false.B)
  val mem_read_reg  = RegInit(false.B)
  val mem_write_reg = RegInit(false.B)

  val is_load_reg = RegInit(false.B)
  val is_lui_reg  = RegInit(false.B)
  val is_jal_reg  = RegInit(false.B)
  val is_jalr_reg = RegInit(false.B)

  val alu_result_reg = RegInit(0.U(32.W))
  val rs2_data_reg   = RegInit(0.U(32.W))
  val rd_reg         = RegInit(0.U(5.W))
  val funct3_reg     = RegInit(0.U(3.W))
  val pc_reg         = RegInit(0.U(32.W))
  val opcode_reg     = RegInit(0.U(7.W))
  val inst_reg       = RegInit(0.U(32.W))
  val imm_reg        = RegInit(0.U(32.W))

  when(FLUSH) {
    mem_op_reg    := 0.U
    reg_write_reg := false.B
    mem_read_reg  := false.B
    mem_write_reg := false.B

    is_load_reg := false.B
    is_lui_reg  := false.B
    is_jal_reg  := false.B
    is_jalr_reg := false.B

    alu_result_reg := 0.U
    rs2_data_reg   := 0.U
    rd_reg         := 0.U
    funct3_reg     := 0.U
    pc_reg         := 0.U
    opcode_reg     := 0.U
    inst_reg       := 0.U
    imm_reg        := 0.U
  }.elsewhen(!STALL) {
    mem_op_reg    := EX_MEM_OP
    reg_write_reg := EX_REG_WRITE
    mem_read_reg  := EX_MEM_READ
    mem_write_reg := EX_MEM_WRITE

    is_load_reg := EX_IS_LOAD
    is_lui_reg  := EX_IS_LUI
    is_jal_reg  := EX_IS_JAL
    is_jalr_reg := EX_IS_JALR

    alu_result_reg := EX_ALU_RESULT
    rs2_data_reg   := EX_RS2_DATA
    rd_reg         := EX_RD
    funct3_reg     := EX_FUNCT3
    pc_reg         := EX_PC
    opcode_reg     := EX_OPCODE
    inst_reg       := EX_INST
    imm_reg        := EX_IMM
  }

  MEM_MEM_OP    := mem_op_reg
  MEM_REG_WRITE := reg_write_reg
  MEM_MEM_READ  := mem_read_reg
  MEM_MEM_WRITE := mem_write_reg

  MEM_IS_LOAD := is_load_reg
  MEM_IS_LUI  := is_lui_reg
  MEM_IS_JAL  := is_jal_reg
  MEM_IS_JALR := is_jalr_reg

  MEM_ALU_RESULT := alu_result_reg
  MEM_RS2_DATA   := rs2_data_reg
  MEM_RD         := rd_reg
  MEM_FUNCT3     := funct3_reg
  MEM_PC         := pc_reg
  MEM_OPCODE     := opcode_reg
  MEM_INST       := inst_reg
  MEM_IMM        := imm_reg
}
