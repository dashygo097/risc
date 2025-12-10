package core

import chisel3._
import chisel3.util._

class MEM_WB extends Module {
  override def desiredName: String = s"mem_wb"

  val stall = IO(Input(Bool()))
  val flush = IO(Input(Bool()))

  // Control signals
  val mem_reg_write = IO(Input(Bool()))

  // Data
  val mem_wb_data = IO(Input(UInt(32.W)))
  val mem_rd      = IO(Input(UInt(5.W)))
  val mem_pc      = IO(Input(UInt(32.W)))
  val mem_opcode  = IO(Input(UInt(7.W)))
  val mem_inst    = IO(Input(UInt(32.W)))

  // Outputs to WB stage
  val wb_reg_write = IO(Output(Bool()))
  val wb_wb_data   = IO(Output(UInt(32.W)))
  val wb_rd        = IO(Output(UInt(5.W)))
  val wb_pc        = IO(Output(UInt(32.W)))
  val wb_opcode    = IO(Output(UInt(7.W)))
  val wb_inst      = IO(Output(UInt(32.W)))

  // Registers
  val reg_write_reg = RegInit(false.B)
  val wb_data_reg   = RegInit(0.U(32.W))
  val rd_reg        = RegInit(0.U(5.W))
  val pc_reg        = RegInit(0.U(32.W))
  val opcode_reg    = RegInit(0.U(7.W))
  val inst_reg      = RegInit(0.U(32.W))

  when(flush) {
    reg_write_reg := false.B
    wb_data_reg   := 0.U
    rd_reg        := 0.U
    pc_reg        := 0.U
    opcode_reg    := 0.U
    inst_reg      := 0.U
  }.elsewhen(!stall) {
    reg_write_reg := mem_reg_write
    wb_data_reg   := mem_wb_data
    rd_reg        := mem_rd
    pc_reg        := mem_pc
    opcode_reg    := mem_opcode
    inst_reg      := mem_inst
  }

  wb_reg_write := reg_write_reg
  wb_wb_data   := wb_data_reg
  wb_rd        := rd_reg
  wb_pc        := pc_reg
  wb_opcode    := opcode_reg
  wb_inst      := inst_reg
}
