package arch.core

import ifu._
import decoder._
import imm._
import bru._
import regfile._
import bpu._
import csr._
import lsu._
import alu._
import mult._
import div._
import ooo._
import arch.configs._
import arch.configs.proto.FunctionalUnitType._
import vopts.mem.cache.{ CacheIO, CacheReadOnlyIO, SetAssociativeCache, SetAssociativeCacheReadOnly }
import chisel3._
import chisel3.util.{ log2Ceil, MuxCase, Mux1H, PopCount, MuxLookup, RRArbiter, Queue }

class RiscCore(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_cpu"

  val imem = IO(new CacheReadOnlyIO(Vec(p(IssueWidth), UInt(p(ILen).W)), p(XLen)))
  val dmem = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))
  val mmio = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))
  val irq  = IO(new CoreInterruptIO)

  val bpu      = Module(new Bpu)
  val ifu      = Module(new Ifu)
  val decoders = Seq.fill(p(IssueWidth))(Module(new Decoder))
  val regfile  = Module(new Regfile)
  val imm_gens = Seq.fill(p(IssueWidth))(Module(new ImmGen))

  val l1_icache = Module(
    new SetAssociativeCacheReadOnly(
      Vec(p(IssueWidth), UInt(p(ILen).W)),
      p(XLen),
      p(L1ICacheLineSize) / (p(IssueWidth) * (p(ILen) / 8)),
      p(L1ICacheSets),
      p(L1ICacheWays),
      p(L1ICacheReplPolicy)
    )
  )

  val l1_dcache = Module(new SetAssociativeCache(UInt(p(XLen).W), p(XLen), p(L1DCacheLineSize) / (p(XLen) / 8), p(L1DCacheSets), p(L1DCacheWays), p(L1DCacheReplPolicy)))

  val regfile_utils = RegfileUtilsFactory.getOrThrow(p(ISA).name)

  val scheduler = Scheduler()
  val rob       = Module(new ReorderBuffer)

  val fus = p(FunctionalUnits).map { fuDesc =>
    fuDesc.`type` match {
      case FUNCTIONAL_UNIT_TYPE_ALU  => Module(new AluFU)
      case FUNCTIONAL_UNIT_TYPE_MULT => Module(new MultFU)
      case FUNCTIONAL_UNIT_TYPE_DIV  => Module(new DivFU)
      case FUNCTIONAL_UNIT_TYPE_BRU  => Module(new BruFU)
      case FUNCTIONAL_UNIT_TYPE_LSU  => Module(new LsuFU)
      case FUNCTIONAL_UNIT_TYPE_CSR  => Module(new CsrFU)
      case _                         => throw new Exception(s"Unknown FunctionalUnitType: ${fuDesc.`type`}")
    }
  }

  val lsus = fus.collect { case l: LsuFU => l }
  val csrs = fus.collect { case c: CsrFU => c }
  val brus = fus.collect { case b: BruFU => b }

  if (lsus.isEmpty) throw new Exception("LSU Unit is mandatory but missing from configuration!")

  val is_flush = Wire(Vec(p(IssueWidth), Bool()))
  for (w <- 0 until p(IssueWidth))
    is_flush(w) := rob.io.commit(w).pop && rob.io.commit(w).flush_pipeline

  val commit_flush_pipeline = is_flush.reduce(_ || _)
  val commit_flush_target   = Mux1H(is_flush.zipWithIndex.map { case (f, w) => f -> rob.io.commit(w).flush_target })

  val async_trap_req = if (csrs.nonEmpty) csrs.map(c => c.trap_request && !c.is_busy).foldLeft(false.B)(_ || _) else false.B
  val async_trap_tgt = if (csrs.nonEmpty) Mux1H(csrs.map(c => (c.trap_request && !c.is_busy) -> c.trap_target)) else 0.U(p(XLen).W)

  val global_flush = commit_flush_pipeline || async_trap_req
  val redirect_pc  = Mux(async_trap_req, async_trap_tgt, commit_flush_target)

  val cache_inflight = RegInit(0.U(log2Ceil(p(ROBSize) + 1).W))
  val mmio_inflight  = RegInit(0.U(log2Ceil(p(ROBSize) + 1).W))

  val mem_req_fire  = l1_dcache.upper.req.fire
  val mem_resp_fire = l1_dcache.upper.resp.fire
  when(mem_req_fire && !mem_resp_fire)(cache_inflight := cache_inflight + 1.U)
    .elsewhen(!mem_req_fire && mem_resp_fire)(cache_inflight := cache_inflight - 1.U)

  val mmio_req_fire  = mmio.req.fire
  val mmio_resp_fire = mmio.resp.fire
  when(mmio_req_fire && !mmio_resp_fire)(mmio_inflight := mmio_inflight + 1.U)
    .elsewhen(!mmio_req_fire && mmio_resp_fire)(mmio_inflight := mmio_inflight - 1.U)

  val mem_draining = RegInit(false.B)
  when(global_flush) {
    when(cache_inflight =/= 0.U || mmio_inflight =/= 0.U)(mem_draining := true.B)
  }.elsewhen(mem_draining && cache_inflight === 0.U && mmio_inflight === 0.U) {
    mem_draining := false.B
  }

  if (lsus.length == 1) {
    l1_dcache.upper <> lsus(0).mem
    mmio <> lsus(0).mmio
  } else {
    val memReqArb = Module(new RRArbiter(chiselTypeOf(lsus(0).mem.req.bits), lsus.length))
    for (i <- lsus.indices) memReqArb.io.in(i) <> lsus(i).mem.req

    val memRespQueue = Module(new Queue(UInt(log2Ceil(lsus.length).W), p(ROBSize)))
    memReqArb.io.out.ready    := l1_dcache.upper.req.ready && memRespQueue.io.enq.ready
    l1_dcache.upper.req.valid := memReqArb.io.out.valid && memRespQueue.io.enq.ready
    l1_dcache.upper.req.bits  := memReqArb.io.out.bits

    memRespQueue.io.enq.valid := memReqArb.io.out.valid && l1_dcache.upper.req.ready
    memRespQueue.io.enq.bits  := memReqArb.io.chosen

    val memTarget = memRespQueue.io.deq.bits
    for (i <- lsus.indices) {
      lsus(i).mem.resp.valid := l1_dcache.upper.resp.valid && memRespQueue.io.deq.valid && (memTarget === i.U)
      lsus(i).mem.resp.bits  := l1_dcache.upper.resp.bits
    }

    l1_dcache.upper.resp.ready := false.B
    memRespQueue.io.deq.ready  := false.B
    when(memRespQueue.io.deq.valid) {
      for (i <- lsus.indices)
        when(memTarget === i.U) {
          val effectively_ready = lsus(i).mem.resp.ready || lsus(i).io.req.ready
          l1_dcache.upper.resp.ready := effectively_ready
          memRespQueue.io.deq.ready  := l1_dcache.upper.resp.valid && effectively_ready
        }
    }

    val mmioReqArb = Module(new RRArbiter(chiselTypeOf(lsus(0).mmio.req.bits), lsus.length))
    for (i <- lsus.indices) mmioReqArb.io.in(i) <> lsus(i).mmio.req

    val mmioRespQueue = Module(new Queue(UInt(log2Ceil(lsus.length).W), p(ROBSize)))
    mmioReqArb.io.out.ready := mmio.req.ready && mmioRespQueue.io.enq.ready
    mmio.req.valid          := mmioReqArb.io.out.valid && mmioRespQueue.io.enq.ready
    mmio.req.bits           := mmioReqArb.io.out.bits

    mmioRespQueue.io.enq.valid := mmioReqArb.io.out.valid && mmio.req.ready
    mmioRespQueue.io.enq.bits  := mmioReqArb.io.chosen

    val mmioTarget = mmioRespQueue.io.deq.bits
    for (i <- lsus.indices) {
      lsus(i).mmio.resp.valid := mmio.resp.valid && mmioRespQueue.io.deq.valid && (mmioTarget === i.U)
      lsus(i).mmio.resp.bits  := mmio.resp.bits
    }

    mmio.resp.ready            := false.B
    mmioRespQueue.io.deq.ready := false.B
    when(mmioRespQueue.io.deq.valid) {
      for (i <- lsus.indices)
        when(mmioTarget === i.U) {
          val effectively_ready = lsus(i).mmio.resp.ready || lsus(i).io.req.ready
          mmio.resp.ready            := effectively_ready
          mmioRespQueue.io.deq.ready := mmio.resp.valid && effectively_ready
        }
    }
  }

  dmem <> l1_dcache.lower
  imem <> l1_icache.lower

  csrs.foreach(csr => csr.arch_pc := Mux(rob.io.empty, ifu.if_pc(0), rob.io.commit(0).pc))

  ifu.mem <> l1_icache.upper

  val bpuQueryBase = ifu.fetch_pc & ~(p(IssueWidth) * (p(ILen) / 8) - 1).U(p(XLen).W)
  for (w <- 0 until p(IssueWidth))
    bpu.query_pc(w) := bpuQueryBase + (w * (p(ILen) / 8)).U
  bpu.advance_valid       := ifu.fetch_fire
  bpu.flush               := global_flush
  ifu.bpu_taken_in        := bpu.taken
  ifu.bpu_target_in       := bpu.target
  ifu.bpu_pht_index_in    := bpu.pht_index
  ifu.bpu_ghr_snapshot_in := bpu.ghr_snapshot

  ifu.take_trap     := global_flush
  ifu.trap_target   := redirect_pc
  ifu.bru_taken     := false.B
  ifu.bru_target    := 0.U
  ifu.bru_not_taken := false.B
  ifu.bru_branch_pc := 0.U

  val aluIds  = p(FunctionalUnits).zipWithIndex.filter(_._1.`type` == FUNCTIONAL_UNIT_TYPE_ALU).map(_._2.U)
  val multIds = p(FunctionalUnits).zipWithIndex.filter(_._1.`type` == FUNCTIONAL_UNIT_TYPE_MULT).map(_._2.U)
  val divIds  = p(FunctionalUnits).zipWithIndex.filter(_._1.`type` == FUNCTIONAL_UNIT_TYPE_DIV).map(_._2.U)
  val lsuIds  = p(FunctionalUnits).zipWithIndex.filter(_._1.`type` == FUNCTIONAL_UNIT_TYPE_LSU).map(_._2.U)
  val bruIds  = p(FunctionalUnits).zipWithIndex.filter(_._1.`type` == FUNCTIONAL_UNIT_TYPE_BRU).map(_._2.U)
  val csrIds  = p(FunctionalUnits).zipWithIndex.filter(_._1.`type` == FUNCTIONAL_UNIT_TYPE_CSR).map(_._2.U)

  val rs1s = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rs2s = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rds  = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))

  val is_csr = Wire(Vec(p(IssueWidth), Bool()))
  val is_lsu = Wire(Vec(p(IssueWidth), Bool()))
  val hazard = Wire(Vec(p(IssueWidth), Bool()))

  val lsu_unready_count = RegInit(0.U(log2Ceil(p(ROBSize) + 1).W))

  var csr_active = !rob.io.empty
  var lsu_active = lsu_unready_count =/= 0.U

  for (w <- 0 until p(IssueWidth)) {
    decoders(w).instr   := ifu.if_instr(w)
    imm_gens(w).instr   := ifu.if_instr(w)
    imm_gens(w).immType := decoders(w).decoded.imm_type

    rs1s(w) := regfile_utils.getRs1(ifu.if_instr(w))
    rs2s(w) := regfile_utils.getRs2(ifu.if_instr(w))
    rds(w)  := regfile_utils.getRd(ifu.if_instr(w))

    regfile.rs1_preg(w) := rs1s(w)
    regfile.rs2_preg(w) := rs2s(w)

    rob.io.rs1_addr(w) := rs1s(w)
    rob.io.rs2_addr(w) := rs2s(w)

    is_csr(w) := decoders(w).decoded.csr || decoders(w).decoded.ret
    is_lsu(w) := decoders(w).decoded.lsu

    val csr_haz = is_csr(w) && (csr_active || w.U > 0.U)
    val lsu_haz = is_lsu(w) && lsu_active

    hazard(w) := csr_haz || lsu_haz

    if (w > 0) {
      val next_csr = WireDefault(csr_active)
      when(is_csr(w))(next_csr := true.B)
      csr_active = next_csr

      val next_lsu = WireDefault(lsu_active)
      when(is_lsu(w))(next_lsu := true.B)
      lsu_active = next_lsu
    }
  }

  val wants_to_issue = Wire(Vec(p(IssueWidth), Bool()))
  val inst_type      = Wire(Vec(p(IssueWidth), UInt(log2Ceil(proto.FunctionalUnitType.values.size).W)))

  val kill_mask = Wire(Vec(p(IssueWidth), Bool()))
  kill_mask(0)   := false.B
  for (w <- 1 until p(IssueWidth))
    kill_mask(w) := kill_mask(w - 1) || (
      ifu.if_valid(w - 1) &&
        ifu.if_bpu_pred_taken(w - 1) &&
        ifu.if_pc(w) === (ifu.if_pc(w - 1) + p(IAlign).U)
    )

  for (w <- 0 until p(IssueWidth)) {
    wants_to_issue(w) := ifu.if_valid(w) && !hazard(w) && !global_flush && !kill_mask(w) && !mem_draining

    inst_type(w) := MuxCase(
      FUNCTIONAL_UNIT_TYPE_ALU.index.U,
      Seq(
        is_lsu(w)                                            -> FUNCTIONAL_UNIT_TYPE_LSU.index.U,
        decoders(w).decoded.div                              -> FUNCTIONAL_UNIT_TYPE_DIV.index.U,
        decoders(w).decoded.mult                             -> FUNCTIONAL_UNIT_TYPE_MULT.index.U,
        decoders(w).decoded.bru                              -> FUNCTIONAL_UNIT_TYPE_BRU.index.U,
        (decoders(w).decoded.csr || decoders(w).decoded.ret) -> FUNCTIONAL_UNIT_TYPE_CSR.index.U
      )
    )
  }

  val intra_hazard = Wire(Vec(p(IssueWidth), Bool()))
  intra_hazard(0) := false.B
  for (w <- 1 until p(IssueWidth)) {
    val conflicts = (0 until w).map { v =>
      val rd_v       = rds(v)
      val we_v       = decoders(v).decoded.regwrite && regfile_utils.writable(rd_v)
      val is_issuing = wants_to_issue(v)

      is_issuing && we_v && (
        (rs1s(w) === rd_v && regfile_utils.readable(rs1s(w))) ||
          (rs2s(w) === rd_v && regfile_utils.readable(rs2s(w)))
      )
    }
    intra_hazard(w) := conflicts.reduce(_ || _)
  }

  val struct_hazard = Wire(Vec(p(IssueWidth), Bool()))
  val target_fu_id  = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(FunctionalUnits).size).W)))

  val alu_rr = RegInit(0.U(log2Ceil(aluIds.length + 1).W))
  val lsu_rr = RegInit(0.U(log2Ceil(lsuIds.length + 1).W))

  def getId(used: UInt, ids: Seq[UInt], rr: UInt = 0.U): UInt =
    if (ids.isEmpty) 0.U
    else MuxLookup((used + rr) % ids.length.U, ids.head)(ids.zipWithIndex.map { case (id, idx) => idx.U -> id })

  for (w <- 0 until p(IssueWidth)) {
    val alu_used  = PopCount((0 until w).map(i => wants_to_issue(i) && !intra_hazard(i) && inst_type(i) === FUNCTIONAL_UNIT_TYPE_ALU.index.U && !struct_hazard(i)))
    val lsu_used  = PopCount((0 until w).map(i => wants_to_issue(i) && !intra_hazard(i) && inst_type(i) === FUNCTIONAL_UNIT_TYPE_LSU.index.U && !struct_hazard(i)))
    val div_used  = PopCount((0 until w).map(i => wants_to_issue(i) && !intra_hazard(i) && inst_type(i) === FUNCTIONAL_UNIT_TYPE_DIV.index.U && !struct_hazard(i)))
    val mult_used = PopCount((0 until w).map(i => wants_to_issue(i) && !intra_hazard(i) && inst_type(i) === FUNCTIONAL_UNIT_TYPE_MULT.index.U && !struct_hazard(i)))
    val bru_used  = PopCount((0 until w).map(i => wants_to_issue(i) && !intra_hazard(i) && inst_type(i) === FUNCTIONAL_UNIT_TYPE_BRU.index.U && !struct_hazard(i)))
    val csr_used  = PopCount((0 until w).map(i => wants_to_issue(i) && !intra_hazard(i) && inst_type(i) === FUNCTIONAL_UNIT_TYPE_CSR.index.U && !struct_hazard(i)))

    struct_hazard(w) := MuxCase(
      false.B,
      Seq(
        (inst_type(w) === FUNCTIONAL_UNIT_TYPE_ALU.index.U)  -> (alu_used >= aluIds.length.U),
        (inst_type(w) === FUNCTIONAL_UNIT_TYPE_LSU.index.U)  -> (lsu_used >= lsuIds.length.U),
        (inst_type(w) === FUNCTIONAL_UNIT_TYPE_DIV.index.U)  -> (div_used >= divIds.length.U),
        (inst_type(w) === FUNCTIONAL_UNIT_TYPE_MULT.index.U) -> (mult_used >= multIds.length.U),
        (inst_type(w) === FUNCTIONAL_UNIT_TYPE_BRU.index.U)  -> (bru_used >= bruIds.length.U),
        (inst_type(w) === FUNCTIONAL_UNIT_TYPE_CSR.index.U)  -> (csr_used >= csrIds.length.U)
      )
    )

    target_fu_id(w) := MuxCase(
      getId(alu_used, aluIds, alu_rr),
      Seq(
        (inst_type(w) === FUNCTIONAL_UNIT_TYPE_LSU.index.U)  -> getId(lsu_used, lsuIds, lsu_rr),
        (inst_type(w) === FUNCTIONAL_UNIT_TYPE_DIV.index.U)  -> getId(div_used, divIds, 0.U),
        (inst_type(w) === FUNCTIONAL_UNIT_TYPE_MULT.index.U) -> getId(mult_used, multIds, 0.U),
        (inst_type(w) === FUNCTIONAL_UNIT_TYPE_BRU.index.U)  -> getId(bru_used, bruIds, 0.U),
        (inst_type(w) === FUNCTIONAL_UNIT_TYPE_CSR.index.U)  -> getId(csr_used, csrIds, 0.U)
      )
    )
  }

  val dis_ready  = Wire(Vec(p(IssueWidth), Bool()))
  val fire       = Wire(Vec(p(IssueWidth), Bool()))
  val lane_valid = Wire(Vec(p(IssueWidth), Bool()))
  val ifu_fire   = Wire(Vec(p(IssueWidth), Bool()))

  for (w <- 0 until p(IssueWidth)) {
    val valid_req = wants_to_issue(w) && !struct_hazard(w) && !intra_hazard(w)
    dis_ready(w) := scheduler.dis_reqs(w).ready && rob.io.enq(w).ready

    val can_consume = kill_mask(w) || (valid_req && dis_ready(w))

    if (w == 0) {
      fire(w)       := can_consume
      lane_valid(w) := valid_req && dis_ready(w)
      ifu_fire(w)   := ifu.if_valid(w) && (global_flush || fire(w))
    } else {
      fire(w)       := can_consume && fire(w - 1)
      lane_valid(w) := valid_req && dis_ready(w) && fire(w - 1)
      ifu_fire(w)   := ifu.if_valid(w) && (global_flush || fire(w)) && ifu_fire(w - 1)
    }
  }

  ifu.dispatch_fire := ifu_fire

  val lsu_dispatched = PopCount((0 until p(IssueWidth)).map(w => lane_valid(w) && is_lsu(w)))
  val lsu_finished   = PopCount(lsus.map(_.io.resp.valid))

  when(global_flush) {
    lsu_unready_count := 0.U
  }.otherwise {
    lsu_unready_count := lsu_unready_count + lsu_dispatched - lsu_finished
  }

  val alu_disp = PopCount((0 until p(IssueWidth)).map(w => lane_valid(w) && inst_type(w) === FUNCTIONAL_UNIT_TYPE_ALU.index.U))
  val lsu_disp = PopCount((0 until p(IssueWidth)).map(w => lane_valid(w) && inst_type(w) === FUNCTIONAL_UNIT_TYPE_LSU.index.U))

  if (aluIds.nonEmpty) alu_rr := (alu_rr + alu_disp) % aluIds.length.U
  if (lsuIds.nonEmpty) lsu_rr := (lsu_rr + lsu_disp) % lsuIds.length.U

  val bpu_update_valid        = WireDefault(false.B)
  val bpu_update_pc           = WireDefault(0.U(p(XLen).W))
  val bpu_update_target       = WireDefault(0.U(p(XLen).W))
  val bpu_update_taken        = WireDefault(false.B)
  val bpu_update_pht_idx      = WireDefault(0.U(p(GShareGhrWidth).W))
  val bpu_update_ghr_snapshot = WireDefault(0.U(p(GShareGhrWidth).W))
  val bpu_update_mispredict   = WireDefault(false.B)
  val branchOpcode            = "b1100011".U(7.W)

  for (w <- p(IssueWidth) - 1 to 0 by -1)
    when(rob.io.commit(w).pop && rob.io.commit(w).is_branch && rob.io.commit(w).instr(6, 0) === branchOpcode) {
      bpu_update_valid        := true.B
      bpu_update_pc           := rob.io.commit(w).pc
      bpu_update_target       := rob.io.commit(w).bpu_actual_target
      bpu_update_taken        := rob.io.commit(w).bpu_actual_taken
      bpu_update_pht_idx      := rob.io.commit(w).bpu_pht_index
      bpu_update_ghr_snapshot := rob.io.commit(w).bpu_ghr_snapshot
      bpu_update_mispredict   := rob.io.commit(w).flush_pipeline
    }

  bpu.update.valid        := bpu_update_valid
  bpu.update.pc           := bpu_update_pc
  bpu.update.target       := bpu_update_target
  bpu.update.taken        := bpu_update_taken
  bpu.update.pht_index    := bpu_update_pht_idx
  bpu.update.ghr_snapshot := bpu_update_ghr_snapshot
  bpu.update.mispredict   := bpu_update_mispredict

  val rs1_commit_match = Wire(Vec(p(IssueWidth), Bool()))
  val rs2_commit_match = Wire(Vec(p(IssueWidth), Bool()))
  val rs1_commit_data  = Wire(Vec(p(IssueWidth), UInt(p(XLen).W)))
  val rs2_commit_data  = Wire(Vec(p(IssueWidth), UInt(p(XLen).W)))

  for (w <- 0 until p(IssueWidth)) {
    val match1 = (0 until p(IssueWidth)).map(cw => rob.io.commit(cw).pop && rob.io.commit(cw).rd === rs1s(w) && rs1s(w) =/= 0.U)
    rs1_commit_match(w) := match1.reduce(_ || _)
    rs1_commit_data(w)  := Mux1H(match1, rob.io.commit.map(_.data))

    val match2 = (0 until p(IssueWidth)).map(cw => rob.io.commit(cw).pop && rob.io.commit(cw).rd === rs2s(w) && rs2s(w) =/= 0.U)
    rs2_commit_match(w) := match2.reduce(_ || _)
    rs2_commit_data(w)  := Mux1H(match2, rob.io.commit.map(_.data))
  }

  for (w <- 0 until p(IssueWidth)) {
    rob.io.enq(w).valid            := lane_valid(w)
    rob.io.enq(w).pc               := ifu.if_pc(w)
    rob.io.enq(w).instr            := ifu.if_instr(w)
    rob.io.enq(w).rd               := Mux(decoders(w).decoded.regwrite && regfile_utils.writable(rds(w)), rds(w), 0.U)
    rob.io.enq(w).pd               := 0.U
    rob.io.enq(w).old_pd           := 0.U
    rob.io.enq(w).is_branch        := decoders(w).decoded.bru
    rob.io.enq(w).is_lsu           := decoders(w).decoded.lsu
    rob.io.enq(w).bpu_pred_taken   := ifu.if_bpu_pred_taken(w)
    rob.io.enq(w).bpu_pred_target  := ifu.if_bpu_pred_target(w)
    rob.io.enq(w).bpu_pht_index    := ifu.if_bpu_pht_index(w)
    rob.io.enq(w).bpu_ghr_snapshot := ifu.if_bpu_ghr_snapshot(w)

    val rs1_bypassed = Mux(rob.io.rs1_bypass(w).valid, rob.io.rs1_bypass(w).data, regfile.rs1_data(w))
    val rs2_bypassed = Mux(rob.io.rs2_bypass(w).valid, rob.io.rs2_bypass(w).data, regfile.rs2_data(w))

    val rs1_fully_bypassed = Mux(rs1_commit_match(w), rs1_commit_data(w), rs1_bypassed)
    val rs2_fully_bypassed = Mux(rs2_commit_match(w), rs2_commit_data(w), rs2_bypassed)

    val dis = scheduler.dis_reqs(w)
    dis.valid         := lane_valid(w)
    dis.bits.pc       := ifu.if_pc(w)
    dis.bits.instr    := ifu.if_instr(w)
    dis.bits.fu_id    := target_fu_id(w)
    dis.bits.rs1      := rs1s(w)
    dis.bits.uop      := decoders(w).decoded.uop
    dis.bits.imm_type := decoders(w).decoded.imm_type
    dis.bits.rs2      := rs2s(w)
    dis.bits.rd       := Mux(decoders(w).decoded.regwrite && regfile_utils.writable(rds(w)), rds(w), 0.U)
    dis.bits.rs1_data := rs1_fully_bypassed
    dis.bits.rs2_data := rs2_fully_bypassed
    dis.bits.rob_tag  := rob.io.enq(w).rob_tag
  }

  scheduler.flush := global_flush
  rob.io.flush    := global_flush

  for ((fu, i) <- fus.zipWithIndex) {
    fu.io.flush := global_flush

    fu.io.req <> scheduler.fu_reqs(i)
    fu.io.resp.ready := true.B

    scheduler.fu_done(i).valid := fu.io.resp.valid
    scheduler.fu_done(i).bits  := fu.io.resp.bits

    rob.io.wb(i).valid   := fu.io.resp.valid
    rob.io.wb(i).rob_tag := fu.io.resp.bits.rob_tag
    rob.io.wb(i).data    := fu.io.resp.bits.result

    rob.io.wb(i).is_bru        := false.B
    rob.io.wb(i).actual_taken  := false.B
    rob.io.wb(i).actual_target := 0.U
    rob.io.wb(i).trap_req      := false.B
    rob.io.wb(i).trap_target   := 0.U
    rob.io.wb(i).trap_ret      := false.B
    rob.io.wb(i).trap_ret_tgt  := 0.U

    fu match {
      case b: BruFU =>
        rob.io.wb(i).is_bru        := true.B
        rob.io.wb(i).actual_taken  := b.actual_taken
        rob.io.wb(i).actual_target := b.actual_target
      case c: CsrFU =>
        rob.io.wb(i).trap_req     := c.trap_request
        rob.io.wb(i).trap_target  := c.trap_target
        rob.io.wb(i).trap_ret     := c.trap_ret
        rob.io.wb(i).trap_ret_tgt := c.trap_ret_tgt
      case _        =>
    }
  }

  for (w <- 0 until p(IssueWidth)) {
    rob.io.read_rob_tag(w) := 0.U
    rob.io.commit(w).pop   := rob.io.commit(w).valid

    regfile.write_en(w)   := rob.io.commit(w).pop && regfile_utils.writable(rob.io.commit(w).rd)
    regfile.write_preg(w) := rob.io.commit(w).rd
    regfile.write_data(w) := rob.io.commit(w).data
  }

  val cycle_count      = RegInit(0.U(64.W))
  val instret_count    = RegInit(0.U(64.W))
  val commit_pop_count = PopCount(rob.io.commit.map(_.pop))

  cycle_count   := cycle_count + 1.U
  instret_count := instret_count + commit_pop_count

  csrs.foreach { csr =>
    csr.cycle         := cycle_count
    csr.instret       := instret_count
    csr.irq.timer_irq := RegNext(irq.timer_irq, false.B)
    csr.irq.soft_irq  := RegNext(irq.soft_irq, false.B)
    csr.irq.ext_irq   := RegNext(irq.ext_irq, false.B)
  }

  val debug_cycle_count   = IO(Output(UInt(64.W)))
  val debug_instret_count = IO(Output(UInt(64.W)))

  debug_cycle_count   := cycle_count
  debug_instret_count := instret_count

  val debug_instret  = IO(Output(Bool()))
  val debug_pc       = IO(Output(UInt(p(XLen).W)))
  val debug_instr    = IO(Output(UInt(p(ILen).W)))
  val debug_reg_we   = IO(Output(Bool()))
  val debug_reg_addr = IO(Output(UInt(log2Ceil(p(NumArchRegs)).W)))
  val debug_reg_data = IO(Output(UInt(p(XLen).W)))

  debug_instret  := rob.io.commit(0).pop
  debug_pc       := rob.io.commit(0).pc
  debug_instr    := rob.io.commit(0).instr
  debug_reg_we   := regfile.write_en(0)
  debug_reg_addr := regfile.write_preg(0)
  debug_reg_data := regfile.write_data(0)

  val debug_branch_taken  = IO(Output(Bool()))
  val debug_branch_source = IO(Output(UInt(p(XLen).W)))
  val debug_branch_target = IO(Output(UInt(p(XLen).W)))

  debug_branch_taken  := bpu_update_valid && bpu_update_taken
  debug_branch_source := bpu_update_pc
  debug_branch_target := bpu_update_target

  val debug_l1_icache_access = IO(Output(Bool()))
  val debug_l1_icache_miss   = IO(Output(Bool()))
  val debug_l1_dcache_access = IO(Output(Bool()))
  val debug_l1_dcache_miss   = IO(Output(Bool()))

  debug_l1_icache_access := RegNext(l1_icache.upper.req.fire)
  debug_l1_icache_miss   := !l1_icache.upper.resp.bits.hit
  debug_l1_dcache_access := RegNext(l1_dcache.upper.req.fire)
  debug_l1_dcache_miss   := !l1_dcache.upper.resp.bits.hit

  val debug_bpu_mispredict = IO(Output(Bool()))
  val debug_branch_commit  = IO(Output(UInt(log2Ceil(p(IssueWidth) + 1).W)))
  val debug_flush_cycle    = IO(Output(Bool()))
  val debug_rob_empty      = IO(Output(Bool()))
  val debug_issue_count    = IO(Output(UInt(log2Ceil(p(IssueWidth) + 1).W)))
  val debug_commit_count   = IO(Output(UInt(log2Ceil(p(IssueWidth) + 1).W)))
  val debug_frontend_stall = IO(Output(Bool()))
  val debug_backend_stall  = IO(Output(Bool()))

  debug_bpu_mispredict := (0 until p(IssueWidth))
    .map(w => rob.io.commit(w).pop && rob.io.commit(w).is_branch && rob.io.commit(w).instr(6, 0) === branchOpcode && rob.io.commit(w).flush_pipeline)
    .reduce(_ || _)
  debug_branch_commit  := PopCount((0 until p(IssueWidth)).map(w => rob.io.commit(w).pop && rob.io.commit(w).is_branch && rob.io.commit(w).instr(6, 0) === branchOpcode))
  debug_flush_cycle    := global_flush
  debug_rob_empty      := rob.io.empty
  debug_issue_count    := PopCount(lane_valid)
  debug_commit_count   := commit_pop_count

  debug_frontend_stall := wants_to_issue(0) && !lane_valid(0)
  debug_backend_stall  := !rob.io.empty && (commit_pop_count === 0.U)
}
