package core

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

  val inner = Module(new DualPortRegFile(32, 32))

  inner.io.rs1_addr   := rs1_addr
  inner.io.rs2_addr   := rs2_addr
  inner.io.write_addr := rd_addr
  inner.io.write_data := write_data

  rs1_data := Mux(inner.io.rs1_fwd, write_data, inner.io.rs1_data)
  rs2_data := Mux(inner.io.rs2_fwd, write_data, inner.io.rs2_data)

  when(rd_we && (rd_addr =/= 0.U)) {
    inner.io.write_en := true.B
  }.otherwise {
    inner.io.write_en := false.B
  }
}

object RV32RegFile extends App {
  VerilogEmitter.parse(new RV32RegFile, "rv32_regfile.sv", info = true)
}
