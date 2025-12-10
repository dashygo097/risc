package core

import chisel3._

class MEM_WB extends Module {
  override def desiredName: String = s"mem_wb"
  val STALL                        = IO(Input(Bool()))
  val FLUSH                        = IO(Input(Bool()))

  // Control signals
  val MEM_REG_WRITE = IO(Input(Bool()))

  // Data
  val MEM_WB_DATA = IO(Input(UInt(32.W)))
  val MEM_RD      = IO(Input(UInt(5.W)))
  val MEM_PC      = IO(Input(UInt(32.W)))
  val MEM_OPCODE  = IO(Input(UInt(7.W)))
  val MEM_INST    = IO(Input(UInt(32.W)))

  // Outputs to WB stage
  val WB_REG_WRITE = IO(Output(Bool()))
  val WB_DATA      = IO(Output(UInt(32.W)))
  val WB_RD        = IO(Output(UInt(5.W)))
  val WB_PC        = IO(Output(UInt(32.W)))
  val WB_OPCODE    = IO(Output(UInt(7.W)))
  val WB_INST      = IO(Output(UInt(32.W)))

  // Registers
  val reg_write_reg = RegInit(false.B)
  val wb_data_reg   = RegInit(0.U(32.W))
  val rd_reg        = RegInit(0.U(5.W))
  val pc_reg        = RegInit(0.U(32.W))
  val opcode_reg    = RegInit(0.U(7.W))
  val inst_reg      = RegInit(0.U(32.W))

  when(FLUSH) {
    reg_write_reg := false.B
    wb_data_reg   := 0.U
    rd_reg        := 0.U
    pc_reg        := 0.U
    opcode_reg    := 0.U
    inst_reg      := 0.U
  }.elsewhen(!STALL) {
    reg_write_reg := MEM_REG_WRITE
    wb_data_reg   := MEM_WB_DATA
    rd_reg        := MEM_RD
    pc_reg        := MEM_PC
    opcode_reg    := MEM_OPCODE
    inst_reg      := MEM_INST
  }

  WB_REG_WRITE := reg_write_reg
  WB_DATA      := wb_data_reg
  WB_RD        := rd_reg
  WB_PC        := pc_reg
  WB_OPCODE    := opcode_reg
  WB_INST      := inst_reg
}
