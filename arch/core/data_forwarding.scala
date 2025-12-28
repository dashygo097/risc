package arch.core

import common.Consts
import regfile.RegfileUtilitiesFactory
import arch.configs._
import chisel3._
import chisel3.util._

trait ForwardingConsts extends Consts {
  def FWD_X    = BitPat("b??")
  def SZ_FWD   = FWD_X.getWidth
  def FWD_SAFE = BitPat("b00")
  def FWD_EX   = BitPat("b01")
  def FWD_MEM  = BitPat("b10")
  def FWD_WB   = BitPat("b11")
}

class IDForwardingUnit(implicit p: Parameters) extends Module with ForwardingConsts {
  override def desiredName: String = s"${p(ISA)}_id_forwarding_unit"

  val regfile_utils = RegfileUtilitiesFactory.getOrThrow(p(ISA))

  val id_rs1       = IO(Input(UInt(regfile_utils.width.W)))
  val id_rs2       = IO(Input(UInt(regfile_utils.width.W)))
  val ex_rd        = IO(Input(UInt(regfile_utils.width.W)))
  val ex_regwrite  = IO(Input(Bool()))
  val mem_rd       = IO(Input(UInt(regfile_utils.width.W)))
  val mem_regwrite = IO(Input(Bool()))
  val wb_rd        = IO(Input(UInt(regfile_utils.width.W)))
  val wb_regwrite  = IO(Input(Bool()))

  val forward_rs1 = IO(Output(UInt(SZ_FWD.W)))
  val forward_rs2 = IO(Output(UInt(SZ_FWD.W)))

  forward_rs1 := MuxCase(
    FWD_SAFE.value.U(SZ_FWD.W),
    Seq(
      (ex_regwrite && (ex_rd =/= 0.U) && (ex_rd === id_rs1))    -> FWD_EX.value.U(SZ_FWD.W),
      (mem_regwrite && (mem_rd =/= 0.U) && (mem_rd === id_rs1)) -> FWD_MEM.value.U(SZ_FWD.W),
      (wb_regwrite && (wb_rd =/= 0.U) && (wb_rd === id_rs1))    -> FWD_WB.value.U(SZ_FWD.W)
    )
  )

  forward_rs2 := MuxCase(
    FWD_SAFE.value.U(SZ_FWD.W),
    Seq(
      (ex_regwrite && (ex_rd =/= 0.U) && (ex_rd === id_rs2))    -> FWD_EX.value.U(SZ_FWD.W),
      (mem_regwrite && (mem_rd =/= 0.U) && (mem_rd === id_rs2)) -> FWD_MEM.value.U(SZ_FWD.W),
      (wb_regwrite && (wb_rd =/= 0.U) && (wb_rd === id_rs2))    -> FWD_WB.value.U(SZ_FWD.W)
    )
  )
}

class EXForwardingUnit(implicit p: Parameters) extends Module with ForwardingConsts {
  override def desiredName: String = s"${p(ISA)}_ex_forwarding_unit"

  val regfile_utils = RegfileUtilitiesFactory.getOrThrow(p(ISA))

  val ex_rs1       = IO(Input(UInt(regfile_utils.width.W)))
  val ex_rs2       = IO(Input(UInt(regfile_utils.width.W)))
  val mem_rd       = IO(Input(UInt(regfile_utils.width.W)))
  val mem_regwrite = IO(Input(Bool()))
  val wb_rd        = IO(Input(UInt(regfile_utils.width.W)))
  val wb_regwrite  = IO(Input(Bool()))

  val forward_rs1 = IO(Output(UInt(SZ_FWD.W)))
  val forward_rs2 = IO(Output(UInt(SZ_FWD.W)))

  forward_rs1 := MuxCase(
    FWD_SAFE.value.U(SZ_FWD.W),
    Seq(
      (mem_regwrite && (mem_rd =/= 0.U) && (mem_rd === ex_rs1)) -> FWD_MEM.value.U(SZ_FWD.W),
      (wb_regwrite && (wb_rd =/= 0.U) && (wb_rd === ex_rs1))    -> FWD_WB.value.U(SZ_FWD.W)
    )
  )

  forward_rs2 := MuxCase(
    FWD_SAFE.value.U(SZ_FWD.W),
    Seq(
      (mem_regwrite && (mem_rd =/= 0.U) && (mem_rd === ex_rs2)) -> FWD_MEM.value.U(SZ_FWD.W),
      (wb_regwrite && (wb_rd =/= 0.U) && (wb_rd === ex_rs2))    -> FWD_WB.value.U(SZ_FWD.W)
    )
  )
}
