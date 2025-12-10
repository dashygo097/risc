package core

import chisel3._

class EX_MEM extends Module {
  override def desiredName: String = s"ex_mem"

  val stall = IO(Input(Bool()))
  val flush = IO(Input(Bool()))

  // Control signals
  val ex_mem_ctrl  = IO(Input(UInt(3.W)))
  val ex_reg_write = IO(Input(Bool()))
  val ex_mem_read  = IO(Input(Bool()))
  val ex_mem_write = IO(Input(Bool()))

  // Data
  val ex_alu_result = IO(Input(UInt(32.W)))
  val ex_rs2_data   = IO(Input(UInt(32.W)))
  val ex_rd         = IO(Input(UInt(5.W)))
  val ex_funct3     = IO(Input(UInt(3.W)))
  val ex_pc         = IO(Input(UInt(32.W)))
  val ex_opcode     = IO(Input(UInt(7.W)))
  val ex_inst       = IO(Input(UInt(32.W)))

  // Outputs to MEM stage
  val mem_mem_ctrl   = IO(Output(UInt(3.W)))
  val mem_reg_write  = IO(Output(Bool()))
  val mem_mem_read   = IO(Output(Bool()))
  val mem_mem_write  = IO(Output(Bool()))
  val mem_alu_result = IO(Output(UInt(32.W)))
  val mem_rs2_data   = IO(Output(UInt(32.W)))
  val mem_rd         = IO(Output(UInt(5.W)))
  val mem_funct3     = IO(Output(UInt(3.W)))
  val mem_pc         = IO(Output(UInt(32.W)))
  val mem_opcode     = IO(Output(UInt(7.W)))
  val mem_inst       = IO(Output(UInt(32.W)))

  // Registers
  val mem_ctrl_reg   = RegInit(0.U(3.W))
  val reg_write_reg  = RegInit(false.B)
  val mem_read_reg   = RegInit(false.B)
  val mem_write_reg  = RegInit(false.B)
  val alu_result_reg = RegInit(0.U(32.W))
  val rs2_data_reg   = RegInit(0.U(32.W))
  val rd_reg         = RegInit(0.U(5.W))
  val funct3_reg     = RegInit(0.U(3.W))
  val pc_reg         = RegInit(0.U(32.W))
  val opcode_reg     = RegInit(0.U(7.W))
  val inst_reg       = RegInit(0.U(32.W))

  when(flush) {
    mem_ctrl_reg   := 0.U
    reg_write_reg  := false.B
    mem_read_reg   := false.B
    mem_write_reg  := false.B
    alu_result_reg := 0.U
    rs2_data_reg   := 0.U
    rd_reg         := 0.U
    funct3_reg     := 0.U
    pc_reg         := 0.U
    opcode_reg     := 0.U
    inst_reg       := 0.U
  }.elsewhen(!stall) {
    mem_ctrl_reg   := ex_mem_ctrl
    reg_write_reg  := ex_reg_write
    mem_read_reg   := ex_mem_read
    mem_write_reg  := ex_mem_write
    alu_result_reg := ex_alu_result
    rs2_data_reg   := ex_rs2_data
    rd_reg         := ex_rd
    funct3_reg     := ex_funct3
    pc_reg         := ex_pc
    opcode_reg     := ex_opcode
    inst_reg       := ex_inst
  }

  mem_mem_ctrl   := mem_ctrl_reg
  mem_reg_write  := reg_write_reg
  mem_mem_read   := mem_read_reg
  mem_mem_write  := mem_write_reg
  mem_alu_result := alu_result_reg
  mem_rs2_data   := rs2_data_reg
  mem_rd         := rd_reg
  mem_funct3     := funct3_reg
  mem_pc         := pc_reg
  mem_opcode     := opcode_reg
  mem_inst       := inst_reg
}
