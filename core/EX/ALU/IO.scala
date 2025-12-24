package core.ex

import chisel3._

class RV32ALUCtrlIO extends Bundle {
  val src1   = Input(UInt(32.W))
  val src2   = Input(UInt(32.W))
  val op     = Input(UInt(3.W))
  val is_sub = Input(Bool())
  val is_sra = Input(Bool())
}

class RV32ALUCtrlExtIO extends Bundle {
  val SRC1   = Input(UInt(32.W))
  val SRC2   = Input(UInt(32.W))
  val OP     = Input(UInt(3.W))
  val IS_SUB = Input(Bool())
  val IS_SRA = Input(Bool())

  def connect(intf: RV32ALUCtrlIO): Unit = {
    intf.src1   := SRC1
    intf.src2   := SRC2
    intf.op     := OP
    intf.is_sub := IS_SUB
    intf.is_sra := IS_SRA
  }
}

class RV32ALUPacketIO extends Bundle {
  val result = Output(UInt(32.W))
}

class RV32ALUPacketExtIO extends Bundle {
  val RESULT = Output(UInt(32.W))

  def connect(intf: RV32ALUPacketIO): Unit =
    RESULT := intf.result
}
