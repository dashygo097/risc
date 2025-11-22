package core.ex

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

  val regs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))

  rs1_data := Mux(rs1_addr === 0.U, 0.U, regs(rs1_addr))
  rs2_data := Mux(rs2_addr === 0.U, 0.U, regs(rs2_addr))

  when(rd_we && (rd_addr =/= 0.U)) {
    regs(rd_addr) := write_data
  }
}

object RV32RegFile extends App {
  VerilogEmitter.parse(new RV32RegFile, "rv32_regfile.sv", info = true)
}
