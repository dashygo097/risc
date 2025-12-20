package core

import chisel3._

class IF_ID extends Module {
  override def desiredName: String = s"if_id"

  val STALL = IO(Input(Bool()))
  val FLUSH = IO(Input(Bool()))

  val IF_PC   = IO(Input(UInt(32.W)))
  val IF_INST = IO(Input(UInt(32.W)))
  val ID_PC   = IO(Output(UInt(32.W)))
  val ID_INST = IO(Output(UInt(32.W)))

  val pc_reg   = RegInit(0.U(32.W))
  val inst_reg = RegInit(0.U(32.W))

  when(FLUSH) {
    pc_reg   := 0.U
    inst_reg := 0x00000013.U // NOP instruction
  }.elsewhen(!STALL) {
    pc_reg   := IF_PC
    inst_reg := IF_INST
  }

  ID_PC   := pc_reg
  ID_INST := inst_reg
}
