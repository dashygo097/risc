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
import pma._
import pipeline._
import forwarding._
import arch.configs._
import mul._
import vopts.mem.cache.{ CacheIO, CacheReadOnlyIO, SetAssociativeCache, SetAssociativeCacheReadOnly }
import chisel3._
import chisel3.util.{ log2Ceil, MuxLookup, MuxCase }

class RiscCore(implicit p: Parameters) extends Module with ForwardingConsts with AluConsts {
  override def desiredName: String = s"${p(ISA)}_cpu"

  val alu_utils     = AluUtilitiesFactory.getOrThrow(p(ISA))
  val bru_utils     = BruUtilitiesFactory.getOrThrow(p(ISA))
  val regfile_utils = RegfileUtilitiesFactory.getOrThrow(p(ISA))
  val lsu_utils     = LsuUtilitiesFactory.getOrThrow(p(ISA))
  val csr_utils     = CsrUtilitiesFactory.getOrThrow(p(ISA))

  val imem = IO(new CacheReadOnlyIO(Vec(p(L1ICacheLineSize) / (p(XLen) / 8), UInt(p(XLen).W)), p(ILen)))
  val dmem = IO(new CacheIO(Vec(p(L1DCacheLineSize) / (p(XLen) / 8), UInt(p(XLen).W)), p(XLen)))
  val mmio = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))

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
  val mul     = Module(new Mul)

  val csrfile: Option[CsrFile] =
    if (p(EnableCSR)) Some(Module(new CsrFile)) else None

  val l1_icache = Module(
    new SetAssociativeCacheReadOnly(
      UInt(p(XLen).W),
      p(XLen),
      p(L1ICacheLineSize) / (p(XLen) / 8),
      p(L1ICacheSets),
      p(L1ICacheWays),
      p(L1ICacheReplPolicy)
    )
  )
  val l1_dcache = Module(
    new SetAssociativeCache(
      UInt(p(XLen).W),
      p(XLen),
      p(L1DCacheLineSize) / (p(XLen) / 8),
      p(L1DCacheSets),
      p(L1DCacheWays),
      p(L1DCacheReplPolicy)
    )
  )

  val if_id = PipelineStageBuilder("if_id")
    .field("instr", p(ILen), p(Bubble).value.toLong)
    .field("pc", p(XLen))
    .field("bpu_pred_taken", 1)
    .field("bpu_pred_target", p(XLen))
    .build()

  val id_ex = PipelineStageBuilder("id_ex")
    .field("instr", p(ILen), p(Bubble).value.toLong)
    .field("pc", p(XLen))
    .field("rd", log2Ceil(p(NumArchRegs)))
    .field("rs1", log2Ceil(p(NumArchRegs)))
    .field("rs1_data", p(XLen))
    .field("rs2", log2Ceil(p(NumArchRegs)))
    .field("rs2_data", p(XLen))
    .field("imm", p(XLen))
    .field("regwrite", 1)
    .field("branch", 1)
    .field("br_type", bru_utils.branchTypeWidth)
    .field("alu", 1)
    .field("alu_sel1", alu_utils.sel1Width)
    .field("alu_sel2", alu_utils.sel2Width)
    .field("alu_fn", alu_utils.fnTypeWidth)
    .field("alu_mode", 1)
    .field("mul_en", 1)
    .field("mul_high", 1)
    .field("mul_a_signed", 1)
    .field("mul_b_signed", 1)
    .field("lsu", 1)
    .field("lsu_cmd", lsu_utils.cmdWidth)
    .addFieldsWhen(p(EnableCSR))(
      Seq(
        PipelineField("csr", 1),
        PipelineField("csr_cmd", csr_utils.cmdWidth),
        PipelineField("csr_addr", csr_utils.addrWidth),
        PipelineField("csr_imm", p(XLen)),
      )
    )
    .build()

  val ex_mem = PipelineStageBuilder("ex_mem")
    .field("instr", p(ILen), p(Bubble).value.toLong)
    .field("pc", p(XLen))
    .field("rd", log2Ceil(p(NumArchRegs)))
    .field("alu_result", p(XLen))
    .field("rs2_data", p(XLen))
    .field("regwrite", 1)
    .field("lsu", 1)
    .field("lsu_cmd", lsu_utils.cmdWidth)
    .addFieldsWhen(p(EnableCSR))(
      Seq(
        PipelineField("csr", 1),
        PipelineField("csr_rdata", p(XLen)),
      )
    )
    .build()

  val mem_wb = PipelineStageBuilder("mem_wb")
    .field("instr", p(ILen), p(Bubble).value.toLong)
    .field("pc", p(XLen), 0L)
    .field("rd", log2Ceil(p(NumArchRegs)), 0L)
    .field("regwrite", 1, 0L)
    .field("wb_data", p(XLen), 0L)
    .build()

  // Control signals
  val load_use_hazard = Wire(Bool())

  // Performance counter
  val cycle_count   = RegInit(0.U(64.W))
  val instret_count = RegInit(0.U(64.W))

  // IF Stage
  imem <> l1_icache.lower
  ifu.mem <> l1_icache.upper

  bpu.query_pc := ifu.fetch_pc

  ifu.bpu_taken_in  := bpu.taken
  ifu.bpu_target_in := bpu.target

  val bpu_correct_taken        = if_id("bpu_pred_taken").asBool &&
    (bru.target === if_id("bpu_pred_target"))
  val bru_mispredict_taken     = bru.taken && !bpu_correct_taken
  val bru_mispredict_not_taken = bru.en && !bru.taken && if_id("bpu_pred_taken").asBool

  ifu.bru_taken       := bru_mispredict_taken
  ifu.bru_target      := bru.target
  ifu.bru_not_taken   := bru_mispredict_not_taken
  ifu.bru_branch_pc   := if_id("pc")
  ifu.id_ex_stall     := id_ex.stall
  ifu.load_use_hazard := load_use_hazard
  ifu.lsu_busy        := lsu.busy

  bpu.update.valid  := bru.en
  bpu.update.pc     := if_id("pc")
  bpu.update.target := bru.target
  bpu.update.taken  := bru.taken

  // IF/ID pipeline
  if_id.stall := ifu.if_id_stall
  if_id.flush := ifu.if_id_flush
  if_id.drive("instr", ifu.if_instr)
  if_id.drive("pc", ifu.if_pc)
  if_id.drive("bpu_pred_taken", ifu.if_bpu_pred_taken)
  if_id.drive("bpu_pred_target", ifu.if_bpu_pred_target)

  // ID Stage
  decoder.instr := if_id("instr")

  imm_gen.instr   := if_id("instr")
  imm_gen.immType := decoder.decoded.imm_type

  val rs1      = regfile_utils.getRs1(if_id("instr"))
  val rs2      = regfile_utils.getRs2(if_id("instr"))
  val rd       = regfile_utils.getRd(if_id("instr"))
  val csr_addr = csr_utils.getAddr(if_id("instr"))

  regfile.rs1_preg := rs1
  regfile.rs2_preg := rs2

  // ID Forwarding
  id_fwd.id_rs1       := rs1
  id_fwd.id_rs2       := rs2
  id_fwd.ex_rd        := id_ex("rd")
  id_fwd.ex_regwrite  := id_ex("regwrite").asBool
  id_fwd.mem_rd       := ex_mem("rd")
  id_fwd.mem_regwrite := ex_mem("regwrite").asBool
  id_fwd.wb_rd        := mem_wb("rd")
  id_fwd.wb_regwrite  := mem_wb("regwrite").asBool

  val id_rs1_data = MuxLookup(id_fwd.forward_rs1, 0.U(p(XLen).W))(
    Seq(
      FWD_SAFE.value.U(SZ_FWD.W) -> regfile.rs1_data,
      FWD_EX.value.U(SZ_FWD.W)   -> alu.result,
      FWD_MEM.value.U(SZ_FWD.W)  -> Mux(lsu.mem_read, lsu.rdata, ex_mem("alu_result")),
      FWD_WB.value.U(SZ_FWD.W)   -> mem_wb("wb_data")
    )
  )

  val id_rs2_data = MuxLookup(id_fwd.forward_rs2, 0.U(p(XLen).W))(
    Seq(
      FWD_SAFE.value.U(SZ_FWD.W) -> regfile.rs2_data,
      FWD_EX.value.U(SZ_FWD.W)   -> alu.result,
      FWD_MEM.value.U(SZ_FWD.W)  -> Mux(lsu.mem_read, lsu.rdata, ex_mem("alu_result")),
      FWD_WB.value.U(SZ_FWD.W)   -> mem_wb("wb_data")
    )
  )

  // BRU
  bru.en     := decoder.decoded.branch && !load_use_hazard
  bru.pc     := if_id("pc")
  bru.src1   := id_rs1_data
  bru.src2   := id_rs2_data
  bru.imm    := imm_gen.imm
  bru.brType := decoder.decoded.br_type

  // Load-use hazard
  load_use_hazard := lsu_utils.isMemRead(id_ex("lsu").asBool, id_ex("lsu_cmd")) &&
    ((id_ex("rd") === rs1) || (id_ex("rd") === rs2)) &&
    id_ex("rd") =/= 0.U

  // MUL stall placeholder (assigned in EX stage)
  val mul_stall = WireDefault(false.B)

  // ID/EX pipeline
  id_ex.stall := lsu.busy || mul_stall
  id_ex.flush := (load_use_hazard ||
    ((bru_mispredict_taken || bru_mispredict_not_taken) && !bru.jump)) &&
    !lsu.busy

  id_ex.drive("instr", if_id("instr"))
  id_ex.drive("pc", if_id("pc"))
  id_ex.drive("rd", rd)
  id_ex.drive("rs1", rs1)
  id_ex.drive("rs1_data", id_rs1_data)
  id_ex.drive("rs2", rs2)
  id_ex.drive("rs2_data", id_rs2_data)
  id_ex.drive("imm", imm_gen.imm)
  id_ex.drive("regwrite", decoder.decoded.regwrite)
  id_ex.drive("branch", decoder.decoded.branch)
  id_ex.drive("br_type", decoder.decoded.br_type)
  id_ex.drive("alu", decoder.decoded.alu)
  id_ex.drive("alu_sel1", decoder.decoded.alu_sel1)
  id_ex.drive("alu_sel2", decoder.decoded.alu_sel2)
  id_ex.drive("alu_fn", decoder.decoded.alu_fn)
  id_ex.drive("alu_mode", decoder.decoded.alu_mode)
  id_ex.drive("mul_en", decoder.decoded.mul_en)
  id_ex.drive("mul_high", decoder.decoded.mul_high)
  id_ex.drive("mul_a_signed", decoder.decoded.mul_a_signed)
  id_ex.drive("mul_b_signed", decoder.decoded.mul_b_signed)
  id_ex.drive("lsu", decoder.decoded.lsu)
  id_ex.drive("lsu_cmd", decoder.decoded.lsu_cmd)
  id_ex.driveOpt("csr", decoder.decoded.csr)
  id_ex.driveOpt("csr_cmd", decoder.decoded.csr_cmd)
  id_ex.driveOpt("csr_addr", csr_addr)
  id_ex.driveOpt("csr_imm", imm_gen.csr_imm)

  // EX Stage
  ex_fwd.ex_rs1       := id_ex("rs1")
  ex_fwd.ex_rs2       := id_ex("rs2")
  ex_fwd.mem_rd       := ex_mem("rd")
  ex_fwd.mem_regwrite := ex_mem("regwrite").asBool
  ex_fwd.wb_rd        := mem_wb("rd")
  ex_fwd.wb_regwrite  := mem_wb("regwrite").asBool

  val ex_rs1_data = MuxLookup(ex_fwd.forward_rs1, 0.U(p(XLen).W))(
    Seq(
      FWD_SAFE.value.U(SZ_FWD.W) -> id_ex("rs1_data"),
      FWD_MEM.value.U(SZ_FWD.W)  -> Mux(lsu.mem_read, lsu.rdata, ex_mem("alu_result")),
      FWD_WB.value.U(SZ_FWD.W)   -> mem_wb("wb_data")
    )
  )

  val ex_rs2_data = MuxLookup(ex_fwd.forward_rs2, 0.U(p(XLen).W))(
    Seq(
      FWD_SAFE.value.U(SZ_FWD.W) -> id_ex("rs2_data"),
      FWD_MEM.value.U(SZ_FWD.W)  -> Mux(lsu.mem_read, lsu.rdata, ex_mem("alu_result")),
      FWD_WB.value.U(SZ_FWD.W)   -> mem_wb("wb_data")
    )
  )

  // ALU
  val alu_rs1_data = MuxLookup(id_ex("alu_sel1"), 0.U(p(XLen).W))(
    Seq(
      A1_ZERO.value.U(SZ_A1.W) -> 0.U(p(XLen).W),
      A1_RS1.value.U(SZ_A1.W)  -> ex_rs1_data,
      A1_PC.value.U(SZ_A1.W)   -> id_ex("pc")
    )
  )

  val alu_rs2_data = MuxLookup(id_ex("alu_sel2"), 0.U(p(XLen).W))(
    Seq(
      A2_ZERO.value.U(SZ_A2.W)   -> 0.U(p(XLen).W),
      A2_RS2.value.U(SZ_A2.W)    -> ex_rs2_data,
      A2_IMM.value.U(SZ_A2.W)    -> id_ex("imm"),
      A2_PCSTEP.value.U(SZ_A2.W) -> p(IAlign).U(p(XLen).W)
    )
  )

  alu.en     := id_ex("alu").asBool
  alu.src1   := alu_rs1_data
  alu.src2   := alu_rs2_data
  alu.fnType := id_ex("alu_fn")
  alu.mode   := id_ex("alu_mode")

  // MUL state (blocking version)
  val mul_req      = id_ex("mul_en").asBool
  val mul_inflight = RegInit(false.B)

  // pure combinational stall to hold EX when mul pending
  mul_stall := mul_req && !mul.io.done

  // one-shot fire when entering mul and multiplier not already inflight
  val mul_fire = mul_req && !mul_inflight && !mul.io.done

  when(id_ex.flush) {
    mul_inflight := false.B
  }.elsewhen(mul_fire) {
    mul_inflight := true.B
  }.elsewhen(mul.io.done) {
    mul_inflight := false.B
  }

  // MUL IO
  mul.io.en        := mul_fire
  mul.io.kill      := id_ex.flush
  mul.io.src1      := ex_rs1_data
  mul.io.src2      := ex_rs2_data
  mul.io.a_signed   := id_ex("mul_a_signed").asBool
  mul.io.b_signed   := id_ex("mul_b_signed").asBool
  mul.io.high      := id_ex("mul_high").asBool

  csrfile.foreach { csr =>
    csr.en   := id_ex("csr").asBool
    csr.cmd  := id_ex("csr_cmd")
    csr.addr := id_ex("csr_addr")
    csr.src  := ex_rs1_data
    csr.imm  := id_ex("csr_imm")
  }

  // EX/MEM pipeline
  ex_mem.stall := mem_wb.stall || lsu.busy || (mul_inflight && !mul.io.done)
  ex_mem.flush := mul_stall

  ex_mem.drive("instr", id_ex("instr"))
  ex_mem.drive("pc", id_ex("pc"))
  ex_mem.drive("rd", id_ex("rd"))
  ex_mem.drive("alu_result", Mux(id_ex("mul_en").asBool, mul.io.result, alu.result))
  ex_mem.drive("rs2_data", ex_rs2_data)
  ex_mem.drive("regwrite", id_ex("regwrite"))
  ex_mem.drive("lsu", id_ex("lsu"))
  ex_mem.drive("lsu_cmd", id_ex("lsu_cmd"))
  ex_mem.driveOpt("csr", id_ex("csr"))
  ex_mem.driveOpt("csr_rdata", csrfile.get.rd)

  // MEM Stage
  lsu.en    := ex_mem("lsu").asBool
  lsu.cmd   := ex_mem("lsu_cmd")
  lsu.addr  := ex_mem("alu_result")
  lsu.wdata := ex_mem("rs2_data")

  val (_, pma_readable, pma_writable, pma_cacheable) = PmaChecker(ex_mem("alu_result"))
  lsu.pma_readable  := pma_readable
  lsu.pma_writable  := pma_writable
  lsu.pma_cacheable := pma_cacheable

  l1_dcache.upper <> lsu.mem
  mmio <> lsu.mmio
  dmem <> l1_dcache.lower

  val l1_dcache_pending = RegInit(false.B)
  when(!l1_dcache.upper.resp.bits.hit) {
    l1_dcache_pending := true.B
  }

  val csrMuxArm: Seq[(Bool, UInt)] =
    if (p(EnableCSR))
      Seq(ex_mem("csr").asBool -> ex_mem("csr_rdata"))
    else
      Nil

  val mem_wb_data = MuxCase(
    ex_mem("alu_result"),
    Seq(lsu.mem_read -> lsu.rdata) ++ csrMuxArm
  )

  // MEM/WB pipeline
  mem_wb.stall := false.B
  mem_wb.flush := lsu.busy
  mem_wb.drive("instr", ex_mem("instr"))
  mem_wb.drive("pc", ex_mem("pc"))
  mem_wb.drive("rd", ex_mem("rd"))
  mem_wb.drive("regwrite", ex_mem("regwrite"))
  mem_wb.drive("wb_data", mem_wb_data)

  // WB Stage
  regfile.write_preg := mem_wb("rd")
  regfile.write_data := mem_wb("wb_data")
  regfile.write_en   := mem_wb("regwrite").asBool

  // Performance counters
  val instret = mem_wb("instr") =/= p(Bubble).value.U(p(ILen).W)

  cycle_count   := cycle_count + 1.U
  instret_count := instret_count + Mux(instret, 1.U, 0.U)

  csrfile.foreach { csr =>
    csr.extraInputIO("cycle")   := cycle_count
    csr.extraInputIO("instret") := instret_count
  }

  // Debug IOs
  val debug_cycle_count   = IO(Output(UInt(64.W)))
  val debug_instret_count = IO(Output(UInt(64.W)))
  val debug_instret       = IO(Output(Bool()))
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
  debug_instret       := instret
  debug_pc            := mem_wb("pc")
  debug_instr         := mem_wb("instr")
  debug_reg_addr      := mem_wb("rd")
  debug_reg_we        := mem_wb("regwrite").asBool
  debug_reg_data      := mem_wb("wb_data")

  debug_branch_taken  := bru.taken
  debug_branch_source := bru.pc
  debug_branch_target := bru.target

  debug_if_instr  := Mux(
    ifu.ibuffer_deq_fire && !ifu.reset_ibuffer,
    ifu.if_instr,
    p(Bubble).value.U(p(ILen).W)
  )
  debug_id_instr  := if_id("instr")
  debug_ex_instr  := id_ex("instr")
  debug_mem_instr := ex_mem("instr")
  debug_wb_instr  := mem_wb("instr")

  debug_l1_icache_access := RegNext(l1_icache.upper.req.fire)
  debug_l1_icache_miss   := !l1_icache.upper.resp.bits.hit
  debug_l1_dcache_access := RegNext(l1_dcache.upper.req.fire)
  debug_l1_dcache_miss   := !l1_dcache.upper.resp.bits.hit
}
