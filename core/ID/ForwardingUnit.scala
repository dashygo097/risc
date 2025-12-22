package core.id

import core.common._
import chisel3._
import chisel3.util._

class IDForwardingUnit extends Module {
  val id_rs1        = IO(Input(UInt(5.W))).suggestName("ID_RS1")
  val id_rs2        = IO(Input(UInt(5.W))).suggestName("ID_RS2")
  val ex_rd         = IO(Input(UInt(5.W))).suggestName("EX_RD")
  val ex_reg_write  = IO(Input(Bool())).suggestName("EX_RegWrite")
  val mem_rd        = IO(Input(UInt(5.W))).suggestName("MEM_RD")
  val mem_reg_write = IO(Input(Bool())).suggestName("MEM_RegWrite")
  val wb_rd         = IO(Input(UInt(5.W))).suggestName("WB_RD")
  val wb_reg_write  = IO(Input(Bool())).suggestName("WB_RegWrite")

  val forward_rs1 = IO(Output(ForwardingStages())).suggestName("FORWARD_RS1")
  val forward_rs2 = IO(Output(ForwardingStages())).suggestName("FORWARD_RS2")

  forward_rs1 := MuxCase(
    ForwardingStages.NONE,
    Seq(
      (ex_reg_write && (ex_rd =/= 0.U) && (ex_rd === id_rs1))    -> ForwardingStages.EX,
      (mem_reg_write && (mem_rd =/= 0.U) && (mem_rd === id_rs1)) -> ForwardingStages.MEM,
      (wb_reg_write && (wb_rd =/= 0.U) && (wb_rd === id_rs1))    -> ForwardingStages.WB
    )
  )

  forward_rs2 := MuxCase(
    ForwardingStages.NONE,
    Seq(
      (ex_reg_write && (ex_rd =/= 0.U) && (ex_rd === id_rs2))    -> ForwardingStages.EX,
      (mem_reg_write && (mem_rd =/= 0.U) && (mem_rd === id_rs2)) -> ForwardingStages.MEM,
      (wb_reg_write && (wb_rd =/= 0.U) && (wb_rd === id_rs2))    -> ForwardingStages.WB
    )
  )
}
