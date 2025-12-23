package core.ex

import core.common._
import chisel3._
import chisel3.util._

class RV32EXForwardingUnit extends Module {
  override def desiredName: String = "rv32_ex_forwarding_unit"
  val ex_rs1                       = IO(Input(UInt(5.W))).suggestName("EX_RS1")
  val ex_rs2                       = IO(Input(UInt(5.W))).suggestName("EX_RS2")
  val mem_rd                       = IO(Input(UInt(5.W))).suggestName("MEM_RD")
  val mem_reg_write                = IO(Input(Bool())).suggestName("MEM_RegWrite")
  val wb_rd                        = IO(Input(UInt(5.W))).suggestName("WB_RD")
  val wb_reg_write                 = IO(Input(Bool())).suggestName("WB_RegWrite")

  val forward_rs1 = IO(Output(UInt(2.W))).suggestName("FORWARD_RS1")
  val forward_rs2 = IO(Output(UInt(2.W))).suggestName("FORWARD_RS2")

  forward_rs1 := MuxCase(
    ForwardingStage.NONE,
    Seq(
      (mem_reg_write && (mem_rd =/= 0.U) && (mem_rd === ex_rs1)) -> ForwardingStage.MEM,
      (wb_reg_write && (wb_rd =/= 0.U) && (wb_rd === ex_rs1))    -> ForwardingStage.WB
    )
  )

  forward_rs2 := MuxCase(
    ForwardingStage.NONE,
    Seq(
      (mem_reg_write && (mem_rd =/= 0.U) && (mem_rd === ex_rs2)) -> ForwardingStage.MEM,
      (wb_reg_write && (wb_rd =/= 0.U) && (wb_rd === ex_rs2))    -> ForwardingStage.WB
    )
  )
}
