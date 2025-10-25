package core

import mem.ram.MMapRegion
import utils._
import chisel3._
import chisel3.util._

object MemOp {
  val LB  = 0.U(3.W)
  val LH  = 1.U(3.W)
  val LW  = 2.U(3.W)
  val LBU = 3.U(3.W)
  val LHU = 4.U(3.W)
  val SB  = 5.U(3.W)
  val SH  = 6.U(3.W)
  val SW  = 7.U(3.W)
}

class RV32GloblMem(addrWidth: Int, dataWidth: Int, memSize: Int, baseAddr: BigInt = 0x0L) extends Module {
  override def desiredName: String =
    s"rv32_globl_mem_${addrWidth}x${dataWidth}_s${memSize}_b$baseAddr"
  val maxAddrValue                 = BigInt(1) << addrWidth
  require(
    memSize > 0 && baseAddr >= 0 && (baseAddr + (memSize * dataWidth)) <= maxAddrValue,
    s"RAM address out of range for addrWidth $addrWidth"
  )

  val mem_ctrl = IO(Input(UInt(3.W)))
  val write_addr = IO(Input(UInt(addrWidth.W)))
  val write_data = IO(Input(UInt(dataWidth.W)))
  val write_resp = IO(Output(Bool()))

  val read_addr = IO(Input(UInt(addrWidth.W)))
  val read_data = IO(Output(UInt(dataWidth.W)))
  val read_resp = IO(Output(Bool()))

  val dataMem = Module(new MMapRegion(
    addrWidth = addrWidth,
    dataWidth = dataWidth,
    memSize   = memSize,
    baseAddr  = baseAddr
  ))

  dataMem.io.write_en  := (mem_ctrl === MemOp.SB) || (mem_ctrl === MemOp.SH) || (mem_ctrl === MemOp.SW)
  dataMem.io.write_addr := write_addr
  dataMem.io.write_data := write_data
  dataMem.io.write_strb := MuxLookup(mem_ctrl, "b0000".U((dataWidth / 8).W))(Seq(
    MemOp.SB -> Cat(Fill((dataWidth / 8) - 1, 0.U(1.W)), 1.U(1.W)),
    MemOp.SH -> Cat(Fill((dataWidth / 8) - 2, 0.U(1.W)), "b11".U(2.W)),
    MemOp.SW -> Fill(dataWidth / 8, 1.U(1.W))
  ))
  write_resp := dataMem.io.write_resp

  dataMem.io.read_en  := (mem_ctrl === MemOp.LB) || (mem_ctrl === MemOp.LH) || (mem_ctrl === MemOp.LW) || (mem_ctrl === MemOp.LBU) || (mem_ctrl === MemOp.LHU)
  dataMem.io.read_addr := read_addr
  read_resp := dataMem.io.read_resp
  read_data := MuxLookup(mem_ctrl, 0.U(dataWidth.W))(Seq(
    MemOp.LB  -> Cat(Fill(24, dataMem.io.read_data(7)), dataMem.io.read_data(7, 0)),
    MemOp.LH  -> Cat(Fill(16, dataMem.io.read_data(15)), dataMem.io.read_data(15, 0)),
    MemOp.LW  -> dataMem.io.read_data,
    MemOp.LBU -> Cat(Fill(24, 0.U), dataMem.io.read_data(7, 0)),
    MemOp.LHU -> Cat(Fill(16, 0.U), dataMem.io.read_data(15, 0))
  ))
}

 object RV32GloblMem extends App {
  VerilogEmitter.parse(new RV32GloblMem(32, 32, 64, 0x0L), "rv32_globl_ctrl_unit.sv", info = true)
}    
