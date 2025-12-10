package core

import chisel3._
import chisel3.util._

class ID_EX extends Module {
  override def desiredName: String = s"id_ex"

  val stall = IO(Input(Bool()))
  val flush = IO(Input(Bool()))

  // Control signals
  val id_alu_ctrl  = IO(Input(UInt(4.W)))
  val id_mem_ctrl  = IO(Input(UInt(3.W)))
  val id_reg_write = IO(Input(Bool()))
  val id_mem_read  = IO(Input(Bool()))
  val id_mem_write = IO(Input(Bool()))

  // Data
  val id_pc       = IO(Input(UInt(32.W)))
  val id_inst     = IO(Input(UInt(32.W)))
  val id_rs1_data = IO(Input(UInt(32.W)))
  val id_rs2_data = IO(Input(UInt(32.W)))
  val id_imm      = IO(Input(UInt(32.W)))
  val id_rd       = IO(Input(UInt(5.W)))
  val id_rs1      = IO(Input(UInt(5.W)))
  val id_rs2      = IO(Input(UInt(5.W)))
  val id_funct3   = IO(Input(UInt(3.W)))
  val id_opcode   = IO(Input(UInt(7.W)))

  // Outputs to EX stage
  val ex_alu_ctrl  = IO(Output(UInt(4.W)))
  val ex_mem_ctrl  = IO(Output(UInt(3.W)))
  val ex_reg_write = IO(Output(Bool()))
  val ex_mem_read  = IO(Output(Bool()))
  val ex_mem_write = IO(Output(Bool()))
  val ex_pc        = IO(Output(UInt(32.W)))
  val ex_inst      = IO(Output(UInt(32.W)))
  val ex_rs1_data  = IO(Output(UInt(32.W)))
  val ex_rs2_data  = IO(Output(UInt(32.W)))
  val ex_imm       = IO(Output(UInt(32.W)))
  val ex_rd        = IO(Output(UInt(5.W)))
  val ex_rs1       = IO(Output(UInt(5.W)))
  val ex_rs2       = IO(Output(UInt(5.W)))
  val ex_funct3    = IO(Output(UInt(3.W)))
  val ex_opcode    = IO(Output(UInt(7.W)))

  // Registers
  val alu_ctrl_reg  = RegInit(0.U(4.W))
  val mem_ctrl_reg  = RegInit(0.U(3.W))
  val reg_write_reg = RegInit(false.B)
  val mem_read_reg  = RegInit(false.B)
  val mem_write_reg = RegInit(false.B)
  val pc_reg        = RegInit(0.U(32.W))
  val inst_reg      = RegInit(0.U(32.W))
  val rs1_data_reg  = RegInit(0.U(32.W))
  val rs2_data_reg  = RegInit(0.U(32.W))
  val imm_reg       = RegInit(0.U(32.W))
  val rd_reg        = RegInit(0.U(5.W))
  val rs1_reg       = RegInit(0.U(5.W))
  val rs2_reg       = RegInit(0.U(5.W))
  val funct3_reg    = RegInit(0.U(3.W))
  val opcode_reg    = RegInit(0.U(7.W))

  when(flush) {
    alu_ctrl_reg  := 0.U
    mem_ctrl_reg  := 0.U
    reg_write_reg := false.B
    mem_read_reg  := false.B
    mem_write_reg := false.B
    pc_reg        := 0.U
    inst_reg      := 0.U
    rs1_data_reg  := 0.U
    rs2_data_reg  := 0.U
    imm_reg       := 0.U
    rd_reg        := 0.U
    rs1_reg       := 0.U
    rs2_reg       := 0.U
    funct3_reg    := 0.U
    opcode_reg    := 0.U
  }.elsewhen(!stall) {
    alu_ctrl_reg  := id_alu_ctrl
    mem_ctrl_reg  := id_mem_ctrl
    reg_write_reg := id_reg_write
    mem_read_reg  := id_mem_read
    mem_write_reg := id_mem_write
    pc_reg        := id_pc
    inst_reg      := id_inst
    rs1_data_reg  := id_rs1_data
    rs2_data_reg  := id_rs2_data
    imm_reg       := id_imm
    rd_reg        := id_rd
    rs1_reg       := id_rs1
    rs2_reg       := id_rs2
    funct3_reg    := id_funct3
    opcode_reg    := id_opcode
  }

  ex_alu_ctrl  := alu_ctrl_reg
  ex_mem_ctrl  := mem_ctrl_reg
  ex_reg_write := reg_write_reg
  ex_mem_read  := mem_read_reg
  ex_mem_write := mem_write_reg
  ex_pc        := pc_reg
  ex_inst      := inst_reg
  ex_rs1_data  := rs1_data_reg
  ex_rs2_data  := rs2_data_reg
  ex_imm       := imm_reg
  ex_rd        := rd_reg
  ex_rs1       := rs1_reg
  ex_rs2       := rs2_reg
  ex_funct3    := funct3_reg
  ex_opcode    := opcode_reg
}
