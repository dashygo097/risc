package arch.core

import ifu._
import decoder._
import imm._
import bru._
import alu._
import regfile._
import lsu._
import csr._
import bpu._
import pipeline._
import arch.configs._
import vopts.mem.cache._
import chisel3._
import chisel3.util._

class RiscCore(implicit p: Parameters) extends Module with ForwardingConsts with AluConsts {
  override def desiredName: String = s"${p(ISA)}_cpu"

  val alu_utils     = AluUtilitiesFactory.getOrThrow(p(ISA))
  val bru_utils     = BruUtilitiesFactory.getOrThrow(p(ISA))
  val regfile_utils = RegfileUtilitiesFactory.getOrThrow(p(ISA))
  val lsu_utils     = LsuUtilitiesFactory.getOrThrow(p(ISA))
  val csr_utils     = CsrUtilitiesFactory.getOrThrow(p(ISA))

  val imem = IO(new CacheReadOnlyIO(Vec(p(L1ICacheLineSize) / (p(XLen) / 8), UInt(p(XLen).W)), p(ILen)))
  val dmem = IO(new CacheIO(Vec(p(L1DCacheLineSize) / (p(XLen) / 8), UInt(p(XLen).W)), p(XLen)))

  // Modules
  val bpu     = Module(new Bpu)
  val ifu     = Module(new Ifu)
  val decoder = Module(new Decoder)
  val bru     = Module(new Bru)
  val regfile = Module(new Regfile)
  val id_fwd  = Module(new IDForwardingUnit)
  val ex_fwd  = Module(new EXForwardingUnit)
  val imm_gen = Module(new ImmGen)
  val alu     = Module(new Alu)
  val lsu     = Module(new Lsu)
  val csrfile = Module(new CsrFile)

  val l1_icache = Module(new SetAssociativeCacheReadOnly(UInt(p(XLen).W), p(XLen), p(L1ICacheLineSize) / (p(XLen) / 8), p(L1ICacheSets), p(L1ICacheWays), p(L1ICacheReplPolicy)))
  val l1_dcache = Module(new SetAssociativeCache(UInt(p(XLen).W), p(XLen), p(L1DCacheLineSize) / (p(XLen) / 8), p(L1DCacheSets), p(L1DCacheWays), p(L1DCacheReplPolicy)))

  // Pipelines
  val if_id = Module(
    new PipelineStage(
      "if_id",
      Seq(
        ("instr", p(ILen), p(Bubble).value.toLong),
        ("pc", p(XLen), 0L),
        ("bpu_pred_taken", 1, 0L),
        ("bpu_pred_target", p(XLen), 0L),
      )
    )
  )

  val id_ex = Module(
    new PipelineStage(
      "id_ex",
      Seq(
        ("instr", p(ILen), p(Bubble).value.toLong),
        ("pc", p(XLen), 0L),
        ("rd", log2Ceil(p(NumArchRegs)), 0L),
        ("rs1", log2Ceil(p(NumArchRegs)), 0L),
        ("rs1_data", p(XLen), 0L),
        ("rs2", log2Ceil(p(NumArchRegs)), 0L),
        ("rs2_data", p(XLen), 0L),
        ("imm", p(XLen), 0L),
        ("csr_addr", csr_utils.addrWidth, 0L),
        ("csr_imm", p(XLen), 0L),
        ("regwrite", 1, 0L),
        ("branch", 1, 0L),
        ("br_type", bru_utils.branchTypeWidth, 0L),
        ("alu", 1, 0L),
        ("alu_sel1", alu_utils.sel1Width, 0L),
        ("alu_sel2", alu_utils.sel2Width, 0L),
        ("alu_fn", alu_utils.fnTypeWidth, 0L),
        ("alu_mode", 1, 0L),
        ("lsu", 1, 0L),
        ("lsu_cmd", lsu_utils.cmdWidth, 0L),
        ("csr", 1, 0L),
        ("csr_cmd", csr_utils.cmdWidth, 0L),
      )
    )
  )

  val ex_mem = Module(
    new PipelineStage(
      "ex_mem",
      Seq(
        ("instr", p(ILen), p(Bubble).value.toLong),
        ("pc", p(XLen), 0L),
        ("rd", log2Ceil(p(NumArchRegs)), 0L),
        ("alu_result", p(XLen), 0L),
        ("rs2_data", p(XLen), 0L),
        ("regwrite", 1, 0L),
        ("lsu", 1, 0L),
        ("lsu_cmd", lsu_utils.cmdWidth, 0L),
        ("csr", 1, 0L),
        ("csr_rdata", p(XLen), 0L),
      )
    )
  )

  val mem_wb = Module(
    new PipelineStage(
      "mem_wb",
      Seq(
        ("instr", p(ILen), p(Bubble).value.toLong),
        ("pc", p(XLen), 0L),
        ("rd", log2Ceil(p(NumArchRegs)), 0L),
        ("regwrite", 1, 0L),
        ("wb_data", p(XLen), 0L),
      )
    )
  )

  // Control Signals
  val load_use_hazard = Wire(Bool())

  // Performance Counters
  val cycle_count   = RegInit(0.U(64.W))
  val instret_count = RegInit(0.U(64.W))

  // IF Stage
  imem <> l1_icache.lower
  ifu.mem <> l1_icache.upper

  bpu.query_pc := ifu.fetch_pc

  ifu.bpu_taken_in  := bpu.taken
  ifu.bpu_target_in := bpu.target

  val bpu_correct_taken = if_id.sout("bpu_pred_taken").asBool &&
    (bru.target === if_id.sout("bpu_pred_target"))

  val bru_mispredict_taken     = bru.taken && !bpu_correct_taken
  val bru_mispredict_not_taken = bru.en && !bru.taken && if_id.sout("bpu_pred_taken").asBool

  ifu.bru_taken       := bru_mispredict_taken
  ifu.bru_target      := bru.target
  ifu.bru_not_taken   := bru_mispredict_not_taken
  ifu.bru_branch_pc   := if_id.sout("pc")
  ifu.id_ex_stall     := id_ex.stall
  ifu.load_use_hazard := load_use_hazard
  ifu.lsu_busy        := lsu.busy

  bpu.update.valid  := bru.en
  bpu.update.pc     := if_id.sout("pc")
  bpu.update.target := bru.target
  bpu.update.taken  := bru.taken

  // IF/ID Pipeline
  if_id.stall                  := ifu.if_id_stall
  if_id.flush                  := ifu.if_id_flush
  if_id.sin("instr")           := ifu.if_instr
  if_id.sin("pc")              := ifu.if_pc
  if_id.sin("bpu_pred_taken")  := ifu.if_bpu_pred_taken
  if_id.sin("bpu_pred_target") := ifu.if_bpu_pred_target

  // ID Stage
  decoder.instr := if_id.sout("instr")

  imm_gen.instr   := if_id.sout("instr")
  imm_gen.immType := decoder.decoded.imm_type

  val rs1      = regfile_utils.getRs1(if_id.sout("instr"))
  val rs2      = regfile_utils.getRs2(if_id.sout("instr"))
  val rd       = regfile_utils.getRd(if_id.sout("instr"))
  val csr_addr = csr_utils.getAddr(if_id.sout("instr"))

  regfile.rs1_preg := rs1
  regfile.rs2_preg := rs2

  // ID Forwarding
  id_fwd.id_rs1       := rs1
  id_fwd.id_rs2       := rs2
  id_fwd.ex_rd        := id_ex.sout("rd")
  id_fwd.ex_regwrite  := id_ex.sout("regwrite").asBool
  id_fwd.mem_rd       := ex_mem.sout("rd")
  id_fwd.mem_regwrite := ex_mem.sout("regwrite").asBool
  id_fwd.wb_rd        := mem_wb.sout("rd")
  id_fwd.wb_regwrite  := mem_wb.sout("regwrite").asBool

  val id_rs1_data = MuxLookup(id_fwd.forward_rs1, 0.U(p(XLen).W))(
    Seq(
      FWD_SAFE.value.U(SZ_FWD.W) -> regfile.rs1_data,
      FWD_EX.value.U(SZ_FWD.W)   -> alu.result,
      FWD_MEM.value.U(SZ_FWD.W)  -> Mux(lsu.mem_read, lsu.rdata, ex_mem.sout("alu_result")),
      FWD_WB.value.U(SZ_FWD.W)   -> mem_wb.sout("wb_data")
    )
  )

  val id_rs2_data = MuxLookup(id_fwd.forward_rs2, 0.U(p(XLen).W))(
    Seq(
      FWD_SAFE.value.U(SZ_FWD.W) -> regfile.rs2_data,
      FWD_EX.value.U(SZ_FWD.W)   -> alu.result,
      FWD_MEM.value.U(SZ_FWD.W)  -> Mux(lsu.mem_read, lsu.rdata, ex_mem.sout("alu_result")),
      FWD_WB.value.U(SZ_FWD.W)   -> mem_wb.sout("wb_data")
    )
  )

  // BRU
  bru.en     := decoder.decoded.branch && !load_use_hazard
  bru.pc     := if_id.sout("pc")
  bru.src1   := id_rs1_data
  bru.src2   := id_rs2_data
  bru.imm    := imm_gen.imm
  bru.brType := decoder.decoded.br_type

  // Load-Use Hazard
  load_use_hazard := lsu_utils.isMemRead(id_ex.sout("lsu").asBool, id_ex.sout("lsu_cmd")) &&
    ((id_ex.sout("rd") === rs1) || (id_ex.sout("rd") === rs2)) &&
    id_ex.sout("rd") =/= 0.U

  // ID/EX Pipeline
  id_ex.stall           := ex_mem.stall
  id_ex.flush           := (load_use_hazard ||
    ((bru_mispredict_taken || bru_mispredict_not_taken) && !bru.jump)) &&
    !lsu.busy
  id_ex.sin("instr")    := if_id.sout("instr")
  id_ex.sin("pc")       := if_id.sout("pc")
  id_ex.sin("rd")       := rd
  id_ex.sin("rs1")      := rs1
  id_ex.sin("rs1_data") := id_rs1_data
  id_ex.sin("rs2")      := rs2
  id_ex.sin("rs2_data") := id_rs2_data
  id_ex.sin("imm")      := imm_gen.imm
  id_ex.sin("csr_addr") := csr_addr
  id_ex.sin("csr_imm")  := imm_gen.csr_imm
  id_ex.sin("regwrite") := decoder.decoded.regwrite
  id_ex.sin("branch")   := decoder.decoded.branch
  id_ex.sin("br_type")  := decoder.decoded.br_type
  id_ex.sin("alu")      := decoder.decoded.alu
  id_ex.sin("alu_sel1") := decoder.decoded.alu_sel1
  id_ex.sin("alu_sel2") := decoder.decoded.alu_sel2
  id_ex.sin("alu_fn")   := decoder.decoded.alu_fn
  id_ex.sin("alu_mode") := decoder.decoded.alu_mode
  id_ex.sin("lsu")      := decoder.decoded.lsu
  id_ex.sin("lsu_cmd")  := decoder.decoded.lsu_cmd
  id_ex.sin("csr")      := decoder.decoded.csr
  id_ex.sin("csr_cmd")  := decoder.decoded.csr_cmd

  // EX Stage
  ex_fwd.ex_rs1       := id_ex.sout("rs1")
  ex_fwd.ex_rs2       := id_ex.sout("rs2")
  ex_fwd.mem_rd       := ex_mem.sout("rd")
  ex_fwd.mem_regwrite := ex_mem.sout("regwrite").asBool
  ex_fwd.wb_rd        := mem_wb.sout("rd")
  ex_fwd.wb_regwrite  := mem_wb.sout("regwrite").asBool

  val ex_rs1_data = MuxLookup(ex_fwd.forward_rs1, 0.U(p(XLen).W))(
    Seq(
      FWD_SAFE.value.U(SZ_FWD.W) -> id_ex.sout("rs1_data"),
      FWD_MEM.value.U(SZ_FWD.W)  -> Mux(lsu.mem_read, lsu.rdata, ex_mem.sout("alu_result")),
      FWD_WB.value.U(SZ_FWD.W)   -> mem_wb.sout("wb_data")
    )
  )

  val ex_rs2_data = MuxLookup(ex_fwd.forward_rs2, 0.U(p(XLen).W))(
    Seq(
      FWD_SAFE.value.U(SZ_FWD.W) -> id_ex.sout("rs2_data"),
      FWD_MEM.value.U(SZ_FWD.W)  -> Mux(lsu.mem_read, lsu.rdata, ex_mem.sout("alu_result")),
      FWD_WB.value.U(SZ_FWD.W)   -> mem_wb.sout("wb_data")
    )
  )

  // ALU
  val alu_rs1_data = MuxLookup(id_ex.sout("alu_sel1"), 0.U(p(XLen).W))(
    Seq(
      A1_ZERO.value.U(SZ_A1.W) -> 0.U(p(XLen).W),
      A1_RS1.value.U(SZ_A1.W)  -> ex_rs1_data,
      A1_PC.value.U(SZ_A1.W)   -> id_ex.sout("pc")
    )
  )

  val alu_rs2_data = MuxLookup(id_ex.sout("alu_sel2"), 0.U(p(XLen).W))(
    Seq(
      A2_ZERO.value.U(SZ_A2.W)   -> 0.U(p(XLen).W),
      A2_RS2.value.U(SZ_A2.W)    -> ex_rs2_data,
      A2_IMM.value.U(SZ_A2.W)    -> id_ex.sout("imm"),
      A2_PCSTEP.value.U(SZ_A2.W) -> p(IAlign).U(p(XLen).W)
    )
  )

  alu.en     := id_ex.sout("alu").asBool
  alu.src1   := alu_rs1_data
  alu.src2   := alu_rs2_data
  alu.fnType := id_ex.sout("alu_fn")
  alu.mode   := id_ex.sout("alu_mode")

  csrfile.en   := id_ex.sout("csr").asBool
  csrfile.cmd  := id_ex.sout("csr_cmd")
  csrfile.addr := id_ex.sout("csr_addr")
  csrfile.src  := ex_rs1_data
  csrfile.imm  := id_ex.sout("csr_imm")

  // EX/MEM Pipeline
  ex_mem.stall             := mem_wb.stall || lsu.busy
  ex_mem.flush             := false.B
  ex_mem.sin("instr")      := id_ex.sout("instr")
  ex_mem.sin("pc")         := id_ex.sout("pc")
  ex_mem.sin("rd")         := id_ex.sout("rd")
  ex_mem.sin("alu_result") := alu.result
  ex_mem.sin("rs2_data")   := ex_rs2_data
  ex_mem.sin("regwrite")   := id_ex.sout("regwrite")
  ex_mem.sin("lsu")        := id_ex.sout("lsu")
  ex_mem.sin("lsu_cmd")    := id_ex.sout("lsu_cmd")
  ex_mem.sin("csr")        := id_ex.sout("csr")
  ex_mem.sin("csr_rdata")  := csrfile.rd

  // MEM Stage
  lsu.en    := ex_mem.sout("lsu").asBool
  lsu.cmd   := ex_mem.sout("lsu_cmd")
  lsu.addr  := ex_mem.sout("alu_result")
  lsu.wdata := ex_mem.sout("rs2_data")

  l1_dcache.upper <> lsu.mem
  dmem <> l1_dcache.lower

  val l1_dcache_pending = RegInit(false.B)
  when(!l1_dcache.upper.resp.bits.hit) {
    l1_dcache_pending := true.B
  }

  val mem_wb_data = MuxCase(
    ex_mem.sout("alu_result"),
    Seq(
      lsu.mem_read              -> lsu.rdata,
      ex_mem.sout("csr").asBool -> ex_mem.sout("csr_rdata")
    )
  )

  // MEM/WB Pipeline
  mem_wb.stall           := false.B
  mem_wb.flush           := lsu.busy
  mem_wb.sin("instr")    := ex_mem.sout("instr")
  mem_wb.sin("pc")       := ex_mem.sout("pc")
  mem_wb.sin("rd")       := ex_mem.sout("rd")
  mem_wb.sin("regwrite") := ex_mem.sout("regwrite")
  mem_wb.sin("wb_data")  := mem_wb_data

  // WB Stage
  regfile.write_preg := mem_wb.sout("rd")
  regfile.write_data := mem_wb.sout("wb_data")
  regfile.write_en   := mem_wb.sout("regwrite").asBool

  // Extra Information
  cycle_count   := cycle_count + 1.U
  instret_count := instret_count + Mux(
    mem_wb.sout("instr") =/= p(Bubble).value.U(p(ILen).W),
    1.U,
    0.U
  )

  csrfile.extraInputIO("cycle")   := cycle_count
  csrfile.extraInputIO("instret") := instret_count

  // Debug
  if (p(IsDebug)) {
    val debug_cycle_count   = IO(Output(UInt(64.W)))
    val debug_instret_count = IO(Output(UInt(64.W)))
    val debug_pc            = IO(Output(UInt(p(XLen).W)))
    val debug_instr         = IO(Output(UInt(p(ILen).W)))
    val debug_reg_we        = IO(Output(Bool()))
    val debug_reg_addr      = IO(Output(UInt(log2Ceil(p(NumArchRegs)).W)))
    val debug_reg_data      = IO(Output(UInt(p(XLen).W)))

    val debug_branch_taken  = IO(Output(Bool()))
    val debug_branch_source = IO(Output(UInt(p(XLen).W)))
    val debug_branch_target = IO(Output(UInt(p(XLen).W)))

    val debug_if_instr  = IO(Output(UInt(p(ILen).W)))
    val debug_id_instr  = IO(Output(UInt(p(ILen).W)))
    val debug_ex_instr  = IO(Output(UInt(p(ILen).W)))
    val debug_mem_instr = IO(Output(UInt(p(ILen).W)))
    val debug_wb_instr  = IO(Output(UInt(p(ILen).W)))

    val debug_l1_icache_access = IO(Output(Bool()))
    val debug_l1_icache_miss   = IO(Output(Bool()))
    val debug_l1_dcache_access = IO(Output(Bool()))
    val debug_l1_dcache_miss   = IO(Output(Bool()))

    debug_cycle_count   := cycle_count
    debug_instret_count := instret_count
    debug_pc            := mem_wb.sout("pc")
    debug_instr         := mem_wb.sout("instr")
    debug_reg_addr      := mem_wb.sout("rd")
    debug_reg_we        := mem_wb.sout("regwrite").asBool
    debug_reg_data      := mem_wb.sout("wb_data")

    // Branch Debugging
    debug_branch_taken  := bru.taken
    debug_branch_source := bru.pc
    debug_branch_target := bru.target

    // Pipelines Debugging
    debug_if_instr  := Mux(ifu.ibuffer_deq_fire && !ifu.reset_ibuffer, ifu.if_instr, p(Bubble).value.U(p(ILen).W))
    debug_id_instr  := if_id.sout("instr")
    debug_ex_instr  := id_ex.sout("instr")
    debug_mem_instr := ex_mem.sout("instr")
    debug_wb_instr  := mem_wb.sout("instr")

    // Cache Debugging
    debug_l1_icache_access := RegNext(l1_icache.upper.req.fire)
    debug_l1_icache_miss   := !l1_icache.upper.resp.bits.hit
    debug_l1_dcache_access := RegNext(l1_dcache.upper.req.fire)
    debug_l1_dcache_miss   := !l1_dcache.upper.resp.bits.hit
  }
}
