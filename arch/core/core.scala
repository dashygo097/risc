package arch.core

import decoder._
import imm._
import alu._
import regfile._
import arch.configs._
import chisel3._
import chisel3.util._

class RiscCore(implicit p: Parameters) extends Module with ForwardingConsts with AluConsts {
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
  val regfile = Module(new Regfile)
  val id_fwd  = Module(new IDForwardingUnit)
  val ex_fwd  = Module(new EXForwardingUnit)
  val imm_gen = Module(new ImmGen)
  val alu     = Module(new Alu)

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

  val rs1 = if_id.ID.instr(19, 15)
  val rs2 = if_id.ID.instr(24, 20)
  val rd  = if_id.ID.instr(11, 7)

  regfile.rs1_addr := rs1
  regfile.rs2_addr := rs2

  id_fwd.id_rs1       := rs1
  id_fwd.id_rs2       := rs2
  id_fwd.ex_rd        := id_ex.EX.rd
  id_fwd.ex_regwrite  := id_ex.EX.decoded_output.regwrite
  id_fwd.mem_rd       := ex_mem.MEM.rd
  id_fwd.mem_regwrite := ex_mem.MEM.regwrite
  id_fwd.wb_rd        := mem_wb.WB.rd
  id_fwd.wb_regwrite  := mem_wb.WB.regwrite

  val id_rs1_data = MuxLookup(id_fwd.forward_rs1, 0.U(p(XLen).W))(
    Seq(
      FWD_SAFE.value.U(SZ_FWD.W) -> regfile.rs1_data,
      FWD_EX.value.U(SZ_FWD.W)   -> alu.result,
      FWD_MEM.value.U(SZ_FWD.W)  -> ex_mem.MEM.alu_result,
      FWD_WB.value.U(SZ_FWD.W)   -> mem_wb.WB.wb_data
    )
  )

  val id_rs2_data = MuxLookup(id_fwd.forward_rs2, 0.U(p(XLen).W))(
    Seq(
      FWD_SAFE.value.U(SZ_FWD.W) -> regfile.rs2_data,
      FWD_EX.value.U(SZ_FWD.W)   -> alu.result,
      FWD_MEM.value.U(SZ_FWD.W)  -> ex_mem.MEM.alu_result,
      FWD_WB.value.U(SZ_FWD.W)   -> mem_wb.WB.wb_data
    )
  )

  // hazard detection
  val load_use_hazard = (id_ex.EX.rd === rs1 && rs1 =/= 0.U) || (id_ex.EX.rd === rs2 && rs2 =/= 0.U)

  stall := false.B

  // branch decision

  flush                   := false.B // for now, no branch implemented
  // ID/EX
  id_ex.STALL             := stall
  id_ex.FLUSH             := flush || stall
  id_ex.ID.decoded_output := decoder.decoded
  id_ex.ID.instr          := if_id.ID.instr
  id_ex.ID.pc             := if_id.ID.pc
  id_ex.ID.rs1            := rs1
  id_ex.ID.rs1_data       := id_rs1_data
  id_ex.ID.rs2            := rs2
  id_ex.ID.rs2_data       := id_rs2_data
  id_ex.ID.rd             := rd

  // EX
  imm_gen.instr   := id_ex.EX.instr
  imm_gen.immType := id_ex.EX.decoded_output.imm_type

  ex_fwd.ex_rs1       := id_ex.EX.rs1
  ex_fwd.ex_rs2       := id_ex.EX.rs2
  ex_fwd.mem_rd       := ex_mem.MEM.rd
  ex_fwd.mem_regwrite := ex_mem.MEM.regwrite
  ex_fwd.wb_rd        := mem_wb.WB.rd
  ex_fwd.wb_regwrite  := mem_wb.WB.regwrite

  val ex_rs1_data = MuxLookup(ex_fwd.forward_rs1, 0.U(p(XLen).W))(
    Seq(
      FWD_SAFE.value.U(SZ_FWD.W) -> id_ex.EX.rs1_data,
      FWD_MEM.value.U(SZ_FWD.W)  -> ex_mem.MEM.alu_result,
      FWD_WB.value.U(SZ_FWD.W)   -> mem_wb.WB.wb_data
    )
  )

  val ex_rs2_data = MuxLookup(ex_fwd.forward_rs2, 0.U(p(XLen).W))(
    Seq(
      FWD_SAFE.value.U(SZ_FWD.W) -> id_ex.EX.rs2_data,
      FWD_MEM.value.U(SZ_FWD.W)  -> ex_mem.MEM.alu_result,
      FWD_WB.value.U(SZ_FWD.W)   -> mem_wb.WB.wb_data
    )
  )

  val alu_rs1_data = MuxLookup(id_ex.EX.decoded_output.alu_sel1, 0.U(p(XLen).W))(
    Seq(
      A1_ZERO.value.U(SZ_A1.W) -> 0.U(p(XLen).W),
      A1_RS1.value.U(SZ_A1.W)  -> ex_rs1_data,
      A1_PC.value.U(SZ_A1.W)   -> id_ex.EX.pc
    )
  )

  val alu_rs2_data = MuxLookup(id_ex.EX.decoded_output.alu_sel2, 0.U(p(XLen).W))(
    Seq(
      A2_ZERO.value.U(SZ_A2.W) -> 0.U(p(XLen).W),
      A2_RS2.value.U(SZ_A2.W)  -> ex_rs2_data,
      A2_IMM.value.U(SZ_A2.W)  -> imm_gen.imm,
    )
  )

  alu.en     := id_ex.EX.decoded_output.alu
  alu.src1   := alu_rs1_data
  alu.src2   := alu_rs2_data
  alu.fnType := id_ex.EX.decoded_output.alu_fn
  alu.mode   := id_ex.EX.decoded_output.alu_mode

  // EX/MEM
  ex_mem.STALL := false.B
  ex_mem.FLUSH := false.B

  ex_mem.EX.alu_result := alu.result
  ex_mem.EX.instr      := id_ex.EX.instr
  ex_mem.EX.pc         := id_ex.EX.pc
  ex_mem.EX.rd         := id_ex.EX.rd
  ex_mem.EX.regwrite   := id_ex.EX.decoded_output.regwrite

  // MEM
  DMEM_ADDR       := ex_mem.MEM.alu_result
  DMEM_READ_EN    := false.B
  DMEM_WRITE_EN   := false.B
  DMEM_WRITE_DATA := ex_rs2_data
  DMEM_WRITE_STRB := "b1111".U((p(XLen) / 8).W)
  val mem_read_data = DMEM_READ_DATA

  // MEM/WB
  mem_wb.STALL        := false.B
  mem_wb.FLUSH        := false.B
  mem_wb.MEM.wb_data  := ex_mem.MEM.alu_result
  mem_wb.MEM.instr    := ex_mem.MEM.instr
  mem_wb.MEM.pc       := ex_mem.MEM.pc
  mem_wb.MEM.rd       := ex_mem.MEM.rd
  mem_wb.MEM.regwrite := ex_mem.MEM.regwrite

  // WB
  regfile.write_addr := mem_wb.WB.rd
  regfile.write_data := mem_wb.WB.wb_data
  regfile.write_en   := mem_wb.WB.regwrite

  // PC update
  next_pc := pc + 4.U

  when(!stall) {
    pc := next_pc
  }

  // debug ports
  DEBUG_PC       := mem_wb.WB.pc
  DEBUG_INST     := mem_wb.WB.instr
  DEBUG_REG_WE   := mem_wb.WB.regwrite
  DEBUG_REG_ADDR := mem_wb.WB.rd
  DEBUG_REG_DATA := mem_wb.WB.wb_data
}
