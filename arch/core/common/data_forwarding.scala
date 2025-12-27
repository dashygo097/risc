package arch.core.common

import arch.core.regfile.RegfileUtilitiesFactory
import arch.configs._
import chisel3._
import chisel3.util._

trait ForwardingConsts extends Consts {
  def F_X    = BitPat("b??")
  def SZ_F   = F_X.getWidth
  def F_SAFE = BitPat("b00")
  def F_EX   = BitPat("b01")
  def F_MEM  = BitPat("b10")
  def F_WB   = BitPat("b11")
}

class IDForwardingUnit(implicit p: Parameters) extends Module with ForwardingConsts {
  override def desiredName: String = s"${p(ISA)}_id_forwarding_unit"

  val utils = RegfileUtilitiesFactory.get(p(ISA)) match {
    case Some(u) => u
    case None    => throw new Exception(s"Regfile utilities for ISA ${p(ISA)} not found!")
  }

  val id_rs1       = IO(Input(UInt(utils.width.W)))
  val id_rs2       = IO(Input(UInt(utils.width.W)))
  val ex_rd        = IO(Input(UInt(utils.width.W)))
  val ex_regwrite  = IO(Input(Bool()))
  val mem_rd       = IO(Input(UInt(utils.width.W)))
  val mem_regwrite = IO(Input(Bool()))
  val wb_rd        = IO(Input(UInt(utils.width.W)))
  val wb_regwrite  = IO(Input(Bool()))

  val forward_rs1 = IO(Output(UInt(2.W)))
  val forward_rs2 = IO(Output(UInt(2.W)))

  forward_rs1 := MuxCase(
    F_SAFE.value.U(SZ_F.W),
    Seq(
      (ex_regwrite && (ex_rd =/= 0.U) && (ex_rd === id_rs1))    -> F_EX.value.U(SZ_F.W),
      (mem_regwrite && (mem_rd =/= 0.U) && (mem_rd === id_rs1)) -> F_MEM.value.U(SZ_F.W),
      (wb_regwrite && (wb_rd =/= 0.U) && (wb_rd === id_rs1))    -> F_WB.value.U(SZ_F.W)
    )
  )

  forward_rs2 := MuxCase(
    F_SAFE.value.U(SZ_F.W),
    Seq(
      (ex_regwrite && (ex_rd =/= 0.U) && (ex_rd === id_rs2))    -> F_EX.value.U(SZ_F.W),
      (mem_regwrite && (mem_rd =/= 0.U) && (mem_rd === id_rs2)) -> F_MEM.value.U(SZ_F.W),
      (wb_regwrite && (wb_rd =/= 0.U) && (wb_rd === id_rs2))    -> F_WB.value.U(SZ_F.W)
    )
  )
}

class EXForwardingUnit(implicit p: Parameters) extends Module with ForwardingConsts {
  override def desiredName: String = s"${p(ISA)}_ex_forwarding_unit"
  val utils                        = RegfileUtilitiesFactory.get(p(ISA)) match {
    case Some(u) => u
    case None    => throw new Exception(s"Regfile utilities for ISA ${p(ISA)} not found!")
  }
  val ex_rs1                       = IO(Input(UInt(utils.width.W)))
  val ex_rs2                       = IO(Input(UInt(utils.width.W)))
  val mem_rd                       = IO(Input(UInt(utils.width.W)))
  val mem_regwrite                 = IO(Input(Bool()))
  val wb_rd                        = IO(Input(UInt(utils.width.W)))
  val wb_regwrite                  = IO(Input(Bool()))

  val forward_rs1 = IO(Output(UInt(2.W)))
  val forward_rs2 = IO(Output(UInt(2.W)))

  forward_rs1 := MuxCase(
    F_SAFE.value.U(SZ_F.W),
    Seq(
      (mem_regwrite && (mem_rd =/= 0.U) && (mem_rd === ex_rs1)) -> F_MEM.value.U(SZ_F.W),
      (wb_regwrite && (wb_rd =/= 0.U) && (wb_rd === ex_rs1))    -> F_WB.value.U(SZ_F.W)
    )
  )

  forward_rs2 := MuxCase(
    F_SAFE.value.U(SZ_F.W),
    Seq(
      (mem_regwrite && (mem_rd =/= 0.U) && (mem_rd === ex_rs2)) -> F_MEM.value.U(SZ_F.W),
      (wb_regwrite && (wb_rd =/= 0.U) && (wb_rd === ex_rs2))    -> F_WB.value.U(SZ_F.W)
    )
  )
}
