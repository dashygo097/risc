package core.mem

import chisel3._
import chisel3.util._

class RV32LSU extends Module {
  override def desiredName: String = s"rv32_lsu"

  // External Interfaces
  val dmem_addr = IO(Output(UInt(32.W))).suggestName("DMEM_ADDR")

  val dmem_write_data = IO(Output(UInt(32.W))).suggestName("DMEM_WRITE_DATA")
  val dmem_write_strb = IO(Output(UInt(4.W))).suggestName("DMEM_WRITE_STRB")
  val dmem_write_en   = IO(Output(Bool())).suggestName("DMEM_WRITE_EN")

  val dmem_read_data = IO(Input(UInt(32.W))).suggestName("DMEM_READ_DATA")
  val dmem_read_en   = IO(Output(Bool())).suggestName("DMEM_READ_EN")

  // Inner Interfaces
  val ctrl_ext = IO(new RV32LSUCtrlExtIO).suggestName("LSU_CTRL")
  val ctrl     = Wire(new RV32LSUCtrlIO)

  val packet_ext = IO(new RV32LSUPacketExtIO).suggestName("LSU_PACKET")
  val packet     = Wire(new RV32LSUPacketIO)

  // Signals
  val byte_offset  = Wire(UInt(2.W))
  val half_offset  = Wire(UInt(1.W))
  val aligned_data = Wire(UInt(32.W))
  val shifted_data = Wire(UInt(32.W))
  val loaded_data  = Wire(UInt(32.W))
  val strb         = Wire(UInt(4.W))

  // Connections
  dmem_addr       := ctrl.addr
  dmem_write_strb := strb

  dmem_write_data := aligned_data
  dmem_write_en   := ctrl.is_store

  dmem_read_en := ctrl.is_load

  packet.data := Mux(ctrl.is_load, loaded_data, 0.U)

  // Logic
  byte_offset := ctrl.addr(1, 0)
  half_offset := ctrl.addr(1)

  aligned_data := MuxLookup(ctrl.size, ctrl.data)(
    Seq(
      "b00".U -> (ctrl.data << (byte_offset << 3)),
      "b01".U -> (ctrl.data << (half_offset << 4)),
      "b10".U -> ctrl.data
    )
  )

  shifted_data := dmem_read_data >> (byte_offset << 3)
  loaded_data  := MuxLookup(ctrl.size, shifted_data)(
    Seq(
      "b00".U -> Mux(
        ctrl.signed && shifted_data(7),
        Cat(Fill(24, 1.U), shifted_data(7, 0)),
        Cat(Fill(24, 0.U), shifted_data(7, 0))
      ),
      "b01".U -> Mux(
        ctrl.signed && shifted_data(15),
        Cat(Fill(16, 1.U), shifted_data(15, 0)),
        Cat(Fill(16, 0.U), shifted_data(15, 0))
      ),
      "b10".U -> shifted_data
    )
  )

  strb := MuxLookup(ctrl.size, "b0000".U)(
    Seq(
      "b00".U -> ("b0001".U << byte_offset),
      "b01".U -> ("b0011".U << half_offset),
      "b10".U -> "b1111".U
    )
  )

  ctrl_ext.connect(ctrl)
  packet_ext.connect(packet)
}
