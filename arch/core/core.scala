package arch.core

import decoder._
import imm._
import alu._
import regfile._
import arch.configs._
import chisel3._
import chisel3.util._

class RiscCore(implicit p: Parameters) extends Module with ForwardingConsts {
  override def desiredName: String = s"${p(ISA)}_cpu"

  val regfile_utils = RegfileUtilitiesFactory.get(p(ISA)) match {
    case Some(u) => u
    case None    => throw new Exception(s"Regfile utilities for ISA ${p(ISA)} not found!")
  }

  // TODO: Frontend Interface need to be impled as well as the cpu simulator
  // TEMPORARY Memory Interface
  val IMEM_ADDR = IO(Output(UInt(p(XLen).W)))
  val IMEM_INST = IO(Input(UInt(p(ILen).W)))

  val DMEM_ADDR       = IO(Output(UInt(p(XLen).W)))
  val DMEM_WRITE_DATA = IO(Output(UInt(p(XLen).W)))
  val DMEM_WRITE_STRB = IO(Output(UInt((p(XLen) / 8).W)))
  val DMEM_WRITE_EN   = IO(Output(Bool()))
  val DMEM_READ_DATA  = IO(Input(UInt(p(XLen).W)))
  val DMEM_READ_EN    = IO(Output(Bool()))

  val DEBUG_PC       = IO(Output(UInt(p(XLen).W)))
  val DEBUG_INST     = IO(Output(UInt(p(ILen).W)))
  val DEBUG_REG_WE   = IO(Output(Bool()))
  val DEBUG_REG_ADDR = IO(Output(UInt(regfile_utils.width.W)))
  val DEBUG_REG_DATA = IO(Output(UInt(p(XLen).W)))

  // Modules
  val decoder = Module(new Decoder)
  val imm_gen = Module(new ImmGen)
  val regfile = Module(new Regfile)
  val id_fwd  = Module(new IDForwardingUnit)
  val ex_fwd  = Module(new EXForwardingUnit)
  val alu     = Module(new ALU)

  // pipeline
  val if_id  = Module(new IF_ID)
  val id_ex  = Module(new ID_EX)
  val ex_mem = Module(new EX_MEM)
  val mem_wb = Module(new MEM_WB)

  // control signals
  val stall = Wire(Bool())
  val flush = Wire(Bool())

  // IF
  val pc      = RegInit(0.U(p(XLen).W))
  val next_pc = Wire(UInt(p(XLen).W))

  IMEM_ADDR := pc

  // IF/ID
  if_id.STALL    := stall
  if_id.FLUSH    := flush
  if_id.IF.pc    := pc
  if_id.IF.instr := IMEM_INST

  // ID
  decoder.instr := if_id.ID.instr
  imm_gen.instr := if_id.ID.instr

  val rs1 = if_id.ID.instr(19, 15)
  val rs2 = if_id.ID.instr(24, 20)
  val rd  = if_id.ID.instr(11, 7)

  id_fwd.id_rs1       := rs1
  id_fwd.id_rs2       := rs2
  id_fwd.ex_rd        := id_ex.EX.rd
  // id_fwd.ex_regwrite  := id_ex.EX.regwrite
  id_fwd.mem_rd       := mem_wb.MEM.rd
  id_fwd.mem_regwrite := mem_wb.MEM.regwrite
  id_fwd.wb_rd        := mem_wb.WB.rd
  id_fwd.wb_regwrite  := mem_wb.WB.regwrite

  val id_rs1_data = MuxLookup(id_fwd.forward_rs1, 0.U(32.W))(
    Seq(
      F_SAFE.value.U(SZ_F.W) -> regfile.rs1_data,
      F_EX.value.U(SZ_F.W)   -> alu.result,
      F_MEM.value.U(SZ_F.W)  -> ex_mem.MEM.alu_result,
      F_WB.value.U(SZ_F.W)   -> mem_wb.WB.wb_data
    )
  )

  val id_rs2_data = MuxLookup(id_fwd.forward_rs2, 0.U(32.W))(
    Seq(
      F_SAFE.value.U(SZ_F.W) -> regfile.rs2_data,
      F_EX.value.U(SZ_F.W)   -> alu.result,
      F_MEM.value.U(SZ_F.W)  -> ex_mem.MEM.alu_result,
      F_WB.value.U(SZ_F.W)   -> mem_wb.WB.wb_data
    )
  )

  // hazard detection

  // branch decision

  // ID/EX
  id_ex.STALL := stall
  id_ex.FLUSH := flush || stall
}
