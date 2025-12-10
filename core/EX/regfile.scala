package core.ex

import mem.register._
import utils._
import chisel3._

class RV32RegFile extends Module {
  override def desiredName: String = s"rv32_regfile"

  val rs1_addr   = IO(Input(UInt(5.W))).suggestName("RS1_ADDR")
  val rs2_addr   = IO(Input(UInt(5.W))).suggestName("RS2_ADDR")
  val write_addr = IO(Input(UInt(5.W))).suggestName("WRITE_ADDR")
  val write_data = IO(Input(UInt(32.W))).suggestName("WRITE_DATA")
  val write_en   = IO(Input(Bool())).suggestName("WRITE_EN")

  val rs1_data = IO(Output(UInt(32.W))).suggestName("RS1_DATA")
  val rs2_data = IO(Output(UInt(32.W))).suggestName("RS2_DATA")

  val dual_port_regfile = Module(
    new DualPortRegFile(
      32,
      32,
      Seq(
        Register("x0", 0x0, 0x0L, writable = false, readable = true),
      )
    )
  )

  dual_port_regfile.rs1_addr   := rs1_addr
  dual_port_regfile.rs2_addr   := rs2_addr
  dual_port_regfile.write_addr := write_addr
  dual_port_regfile.write_data := write_data
  dual_port_regfile.write_en   := write_en
  rs1_data                     := dual_port_regfile.rs1_data
  rs2_data                     := dual_port_regfile.rs2_data
}
