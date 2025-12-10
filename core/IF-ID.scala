package core

import chisel3._

class IF_ID extends Module {
  override def desiredName: String = s"if_id"

  val stall = IO(Input(Bool()))
  val flush = IO(Input(Bool()))

  // Inputs from IF stage
  val if_pc   = IO(Input(UInt(32.W)))
  val if_inst = IO(Input(UInt(32.W)))

  // Outputs to ID stage
  val id_pc   = IO(Output(UInt(32.W)))
  val id_inst = IO(Output(UInt(32.W)))

  val pc_reg   = RegInit(0.U(32.W))
  val inst_reg = RegInit(0.U(32.W))

  when(flush) {
    pc_reg   := 0.U
    inst_reg := 0.U
  }.elsewhen(!stall) {
    pc_reg   := if_pc
    inst_reg := if_inst
  }

  id_pc   := pc_reg
  id_inst := inst_reg
}
