package core.mem

import chisel3._

class RV32LSUCtrlIO extends Bundle {
  val addr     = Input(UInt(32.W))
  val data     = Input(UInt(32.W))
  val signed   = Input(Bool())
  val size     = Input(UInt(2.W)) // 00: byte, 01: half, 10: word
  val is_load  = Input(Bool())
  val is_store = Input(Bool())
}

class RV32LSUCtrlExtIO extends Bundle {
  val ADDR     = Input(UInt(32.W))
  val DATA     = Input(UInt(32.W))
  val SIGNED   = Input(Bool())
  val SIZE     = Input(UInt(2.W)) // 00: byte, 01: half, 10: word
  val IS_STORE = Input(Bool())
  val IS_LOAD  = Input(Bool())

  def connect(intf: RV32LSUCtrlIO): Unit = {
    intf.addr     := ADDR
    intf.data     := DATA
    intf.signed   := SIGNED
    intf.size     := SIZE
    intf.is_load  := IS_LOAD
    intf.is_store := IS_STORE
  }
}

class RV32LSUPacketIO extends Bundle {
  val data = Output(UInt(32.W))
}

class RV32LSUPacketExtIO extends Bundle {
  val DATA = Output(UInt(32.W))

  def connect(intf: RV32LSUPacketIO): Unit =
    DATA := intf.data
}
