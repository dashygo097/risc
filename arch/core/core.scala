package arch.core

import decoder._
import imm._
import bru._
import alu._
import regfile._
import lsu._
import arch.configs._
import mem.cache._
import chisel3._
import chisel3.util._

class RiscCore(implicit p: Parameters) extends Module with ForwardingConsts with AluConsts {
  override def desiredName: String = s"${p(ISA)}_cpu"

  val regfile_utils = RegfileUtilitiesFactory.getOrThrow(p(ISA))
  val bru_utils     = BruUtilitiesFactory.getOrThrow(p(ISA))
  val alu_utils     = AluUtilitiesFactory.getOrThrow(p(ISA))
  val lsu_utils     = LsuUtilitiesFactory.getOrThrow(p(ISA))

  val imem = IO(new UnifiedMemoryIO(p(XLen), p(XLen), 1, 1))
  val dmem = IO(new UnifiedMemoryIO(p(XLen), p(XLen), 1, 1))

  // Modules
  val pipeline_ctrl = Module(new PipelineController)
  val decoder       = Module(new Decoder)
  val bru           = Module(new Bru)
  val regfile       = Module(new Regfile)
  val id_fwd        = Module(new IDForwardingUnit)
  val ex_fwd        = Module(new EXForwardingUnit)
  val imm_gen       = Module(new ImmGen)
  val alu           = Module(new Alu)
  val lsu           = Module(new Lsu)

  // Pipelines
  val if_id  = Module(new IF_ID)
  val id_ex  = Module(new ID_EX)
  val ex_mem = Module(new EX_MEM)
  val mem_wb = Module(new MEM_WB)

  // Control Signals
  val imem_pending = RegInit(false.B)
  val imem_data    = RegInit(0.U(p(ILen).W))

  val load_use_hazard = Wire(Bool())

  val branch_pc        = RegInit(0.U(p(XLen).W))
  val branch_target    = RegInit(0.U(p(XLen).W))
  val branch_taken_reg = RegInit(false.B)

  pipeline_ctrl.if_imem_pending    := imem_pending
  pipeline_ctrl.id_load_use_hazard := load_use_hazard
  pipeline_ctrl.mem_dmem_pending   := lsu.pending

  // IF
  val pc      = RegInit(0.U(p(XLen).W))
  val next_pc = Wire(UInt(p(XLen).W))

  imem.req.valid     := !imem_pending && !pipeline_ctrl.if_id_stall
  imem.req.bits.op   := MemoryOp.READ
  imem.req.bits.addr := pc
  imem.req.bits.data := 0.U(p(XLen).W)
  imem.resp.ready    := true.B

  when(imem.req.fire) {
    imem_pending := true.B
  }

  when(imem.resp.fire) {
    imem_data    := imem.resp.bits.data
    imem_pending := false.B
  }

  // IF/ID
  if_id.STALL    := pipeline_ctrl.if_id_stall
  if_id.FLUSH    := pipeline_ctrl.if_id_flush // branch_taken_reg
  if_id.IF.pc    := pc
  if_id.IF.instr := imem_data

  // ID
  decoder.instr := if_id.ID.instr

  imm_gen.instr   := if_id.ID.instr
  imm_gen.immType := decoder.decoded.imm_type

  val rs1 = regfile_utils.getRs1(if_id.ID.instr)
  val rs2 = regfile_utils.getRs2(if_id.ID.instr)
  val rd  = regfile_utils.getRd(if_id.ID.instr)

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

  // BRU
  bru.en     := decoder.decoded.branch
  bru.pc     := if_id.ID.pc
  bru.src1   := id_rs1_data
  bru.src2   := id_rs2_data
  bru.imm    := imm_gen.imm
  bru.brType := decoder.decoded.br_type

  when(bru.taken && !branch_taken_reg) {
    branch_taken_reg := true.B
    branch_target    := bru.target
  }.otherwise {
    branch_taken_reg := false.B
  }

  // Hazard Detection
  load_use_hazard := lsu_utils.isMemRead(id_ex.EX.decoded_output.lsu, id_ex.EX.decoded_output.lsu_cmd) &&
    ((id_ex.EX.rd === rs1) || (id_ex.EX.rd === rs2))

  // ID/EX
  id_ex.STALL             := pipeline_ctrl.id_ex_stall
  id_ex.FLUSH             := pipeline_ctrl.id_ex_flush // bru.taken && !jump
  id_ex.ID.decoded_output := decoder.decoded
  id_ex.ID.instr          := if_id.ID.instr
  id_ex.ID.pc             := if_id.ID.pc
  id_ex.ID.rd             := rd
  id_ex.ID.rs1            := rs1
  id_ex.ID.rs1_data       := id_rs1_data
  id_ex.ID.rs2            := rs2
  id_ex.ID.rs2_data       := id_rs2_data
  id_ex.ID.imm            := imm_gen.imm

  // EX
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

  // ALU
  val alu_rs1_data = MuxLookup(id_ex.EX.decoded_output.alu_sel1, 0.U(p(XLen).W))(
    Seq(
      A1_ZERO.value.U(SZ_A1.W) -> 0.U(p(XLen).W),
      A1_RS1.value.U(SZ_A1.W)  -> ex_rs1_data,
      A1_PC.value.U(SZ_A1.W)   -> id_ex.EX.pc
    )
  )

  // TODO: A2_FOUR not always the case for different ISAs
  val alu_rs2_data = MuxLookup(id_ex.EX.decoded_output.alu_sel2, 0.U(p(XLen).W))(
    Seq(
      A2_ZERO.value.U(SZ_A2.W) -> 0.U(p(XLen).W),
      A2_RS2.value.U(SZ_A2.W)  -> ex_rs2_data,
      A2_IMM.value.U(SZ_A2.W)  -> id_ex.EX.imm,
      A2_FOUR.value.U(SZ_A2.W) -> 4.U(p(XLen).W)
    )
  )

  alu.en     := id_ex.EX.decoded_output.alu
  alu.src1   := alu_rs1_data
  alu.src2   := alu_rs2_data
  alu.fnType := id_ex.EX.decoded_output.alu_fn
  alu.mode   := id_ex.EX.decoded_output.alu_mode

  // EX/MEM
  ex_mem.STALL         := pipeline_ctrl.ex_mem_stall
  ex_mem.FLUSH         := pipeline_ctrl.ex_mem_flush
  ex_mem.EX.alu_result := alu.result
  ex_mem.EX.instr      := id_ex.EX.instr
  ex_mem.EX.pc         := id_ex.EX.pc
  ex_mem.EX.rd         := id_ex.EX.rd
  ex_mem.EX.rs2_data   := ex_rs2_data
  ex_mem.EX.regwrite   := id_ex.EX.decoded_output.regwrite
  ex_mem.EX.lsu        := id_ex.EX.decoded_output.lsu
  ex_mem.EX.lsu_cmd    := id_ex.EX.decoded_output.lsu_cmd

  // MEM
  lsu.en    := ex_mem.MEM.lsu
  lsu.cmd   := ex_mem.MEM.lsu_cmd
  lsu.addr  := ex_mem.MEM.alu_result
  lsu.wdata := ex_mem.MEM.rs2_data

  dmem <> lsu.mem

  val mem_wb_data = Mux(
    dmem.resp.fire,
    lsu.rdata,
    ex_mem.MEM.alu_result
  )

  // MEM/WB
  mem_wb.STALL        := pipeline_ctrl.mem_wb_stall
  mem_wb.FLUSH        := pipeline_ctrl.mem_wb_flush
  mem_wb.MEM.wb_data  := mem_wb_data
  mem_wb.MEM.instr    := ex_mem.MEM.instr
  mem_wb.MEM.pc       := ex_mem.MEM.pc
  mem_wb.MEM.rd       := ex_mem.MEM.rd
  mem_wb.MEM.regwrite := ex_mem.MEM.regwrite

  // WB
  regfile.write_addr := mem_wb.WB.rd
  regfile.write_data := mem_wb.WB.wb_data
  regfile.write_en   := mem_wb.WB.regwrite

  // PC Update Logic
  next_pc := Mux(bru.taken, bru.target, pc + 4.U(p(XLen).W))
  when(pipeline_ctrl.pc_should_update) {
    pc := next_pc
  }

  // Debug
  if (p(IsDebug)) {
    val debug_cycles   = IO(Output(UInt(64.W)))
    val debug_pc       = IO(Output(UInt(p(XLen).W)))
    val debug_instr    = IO(Output(UInt(p(ILen).W)))
    val debug_reg_we   = IO(Output(Bool()))
    val debug_reg_addr = IO(Output(UInt(regfile_utils.width.W)))
    val debug_reg_data = IO(Output(UInt(p(XLen).W)))

    val cycle_counter = RegInit(0.U(64.W))

    // cycle counter
    when(!cycle_counter(63)) { // prevent overflow
      cycle_counter := cycle_counter + 1.U
    }

    // debug ports
    debug_cycles   := cycle_counter
    debug_pc       := mem_wb.WB.pc
    debug_instr    := mem_wb.WB.instr
    debug_reg_addr := mem_wb.WB.rd
    debug_reg_we   := mem_wb.WB.regwrite
    debug_reg_data := mem_wb.WB.wb_data
  }
}
