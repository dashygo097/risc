package core

import chisel3._

class ID_EX extends Module {
  override def desiredName: String = s"id_ex"
  val STALL                        = IO(Input(Bool()))
  val FLUSH                        = IO(Input(Bool()))

  // Control signals
  val ID_ALU_OP_R   = IO(Input(UInt(3.W)))
  val ID_ALU_IS_SUB = IO(Input(Bool()))
  val ID_ALU_IS_SRA = IO(Input(Bool()))
  val ID_MEM_CTRL   = IO(Input(UInt(3.W)))
  val ID_REG_WRITE  = IO(Input(Bool()))
  val ID_MEM_READ   = IO(Input(Bool()))
  val ID_MEM_WRITE  = IO(Input(Bool()))

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
  val EX_ALU_OP_R   = IO(Output(UInt(3.W)))
  val EX_ALU_IS_SUB = IO(Output(Bool()))
  val EX_ALU_IS_SRA = IO(Output(Bool()))
  val EX_MEM_CTRL   = IO(Output(UInt(3.W)))
  val EX_REG_WRITE  = IO(Output(Bool()))
  val EX_MEM_READ   = IO(Output(Bool()))
  val EX_MEM_WRITE  = IO(Output(Bool()))
  val EX_PC         = IO(Output(UInt(32.W)))
  val EX_INST       = IO(Output(UInt(32.W)))
  val EX_RS1_DATA   = IO(Output(UInt(32.W)))
  val EX_RS2_DATA   = IO(Output(UInt(32.W)))
  val EX_IMM        = IO(Output(UInt(32.W)))
  val EX_RD         = IO(Output(UInt(5.W)))
  val EX_RS1        = IO(Output(UInt(5.W)))
  val EX_RS2        = IO(Output(UInt(5.W)))
  val EX_FUNCT3     = IO(Output(UInt(3.W)))
  val EX_OPCODE     = IO(Output(UInt(7.W)))

  // Registers
  val alu_op_r_reg   = RegInit(0.U(3.W))
  val alu_is_sub_reg = RegInit(false.B)
  val alu_is_sra_reg = RegInit(false.B)
  val mem_ctrl_reg   = RegInit(0.U(3.W))
  val reg_write_reg  = RegInit(false.B)
  val mem_read_reg   = RegInit(false.B)
  val mem_write_reg  = RegInit(false.B)
  val pc_reg         = RegInit(0.U(32.W))
  val inst_reg       = RegInit(0.U(32.W))
  val rs1_data_reg   = RegInit(0.U(32.W))
  val rs2_data_reg   = RegInit(0.U(32.W))
  val imm_reg        = RegInit(0.U(32.W))
  val rd_reg         = RegInit(0.U(5.W))
  val rs1_reg        = RegInit(0.U(5.W))
  val rs2_reg        = RegInit(0.U(5.W))
  val funct3_reg     = RegInit(0.U(3.W))
  val opcode_reg     = RegInit(0.U(7.W))

  when(FLUSH) {
    alu_op_r_reg   := 0.U
    alu_is_sub_reg := false.B
    alu_is_sra_reg := false.B
    mem_ctrl_reg   := 0.U
    reg_write_reg  := false.B
    mem_read_reg   := false.B
    mem_write_reg  := false.B
    pc_reg         := 0.U
    inst_reg       := 0.U
    rs1_data_reg   := 0.U
    rs2_data_reg   := 0.U
    imm_reg        := 0.U
    rd_reg         := 0.U
    rs1_reg        := 0.U
    rs2_reg        := 0.U
    funct3_reg     := 0.U
    opcode_reg     := 0.U
  }.elsewhen(!STALL) {
    alu_op_r_reg   := ID_ALU_OP_R
    alu_is_sub_reg := ID_ALU_IS_SUB
    alu_is_sra_reg := ID_ALU_IS_SRA
    mem_ctrl_reg   := ID_MEM_CTRL
    reg_write_reg  := ID_REG_WRITE
    mem_read_reg   := ID_MEM_READ
    mem_write_reg  := ID_MEM_WRITE
    pc_reg         := ID_PC
    inst_reg       := ID_INST
    rs1_data_reg   := ID_RS1_DATA
    rs2_data_reg   := ID_RS2_DATA
    imm_reg        := ID_IMM
    rd_reg         := ID_RD
    rs1_reg        := ID_RS1
    rs2_reg        := ID_RS2
    funct3_reg     := ID_FUNCT3
    opcode_reg     := ID_OPCODE
  }

  EX_ALU_OP_R   := alu_op_r_reg
  EX_ALU_IS_SUB := alu_is_sub_reg
  EX_ALU_IS_SRA := alu_is_sra_reg
  EX_MEM_CTRL   := mem_ctrl_reg
  EX_REG_WRITE  := reg_write_reg
  EX_MEM_READ   := mem_read_reg
  EX_MEM_WRITE  := mem_write_reg
  EX_PC         := pc_reg
  EX_INST       := inst_reg
  EX_RS1_DATA   := rs1_data_reg
  EX_RS2_DATA   := rs2_data_reg
  EX_IMM        := imm_reg
  EX_RD         := rd_reg
  EX_RS1        := rs1_reg
  EX_RS2        := rs2_reg
  EX_FUNCT3     := funct3_reg
  EX_OPCODE     := opcode_reg
}
