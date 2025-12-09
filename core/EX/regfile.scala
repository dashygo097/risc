package core.ex

import mem.register._
import utils._
import chisel3._

class RV32RegFile extends Module {
  override def desiredName: String = s"rv32_regfile"

  val rs1_addr   = IO(Input(UInt(5.W)))
  val rs2_addr   = IO(Input(UInt(5.W)))
  val rd_addr    = IO(Input(UInt(5.W)))
  val write_data = IO(Input(UInt(32.W)))
  val rd_we      = IO(Input(Bool()))

  val rs1_data = IO(Output(UInt(32.W)))
  val rs2_data = IO(Output(UInt(32.W)))

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
  dual_port_regfile.write_addr := rd_addr
  dual_port_regfile.write_data := write_data
  dual_port_regfile.write_en   := rd_we
  rs1_data                     := dual_port_regfile.rs1_data
  rs2_data                     := dual_port_regfile.rs2_data
}
