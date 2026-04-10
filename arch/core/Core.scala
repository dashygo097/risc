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
import ooo._
import arch.configs._
import arch.configs.proto.FunctionalUnitType._
import vopts.mem.cache.{ CacheIO, CacheReadOnlyIO, SetAssociativeCache, SetAssociativeCacheReadOnly }
import chisel3._
import chisel3.util.{ log2Ceil, MuxCase, Mux1H, PopCount, MuxLookup, RRArbiter, Queue }

class RiscCore(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_cpu"

  val imem = IO(new CacheReadOnlyIO(Vec(p(L1ICacheLineSize) / (p(XLen) / 8), UInt(p(XLen).W)), p(ILen)))
  val dmem = IO(new CacheIO(Vec(p(L1DCacheLineSize) / (p(XLen) / 8), UInt(p(XLen).W)), p(XLen)))
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

  val regfile_utils = RegfileUtilitiesFactory.getOrThrow(p(ISA).name)

  val scheduler = Scheduler()
  val rob       = Module(new ReorderBuffer)

  val fus = p(FunctionalUnits).map { fuDesc =>
    fuDesc.`type` match {
      case FUNCTIONAL_UNIT_TYPE_ALU  => Module(new AluFU)
      case FUNCTIONAL_UNIT_TYPE_MULT => Module(new MultFU)
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

  if (lsus.length == 1) {
    l1_dcache.upper <> lsus(0).mem
    mmio <> lsus(0).mmio
  } else {
    val memReqArb = Module(new RRArbiter(chiselTypeOf(lsus(0).mem.req.bits), lsus.length))
    for (i <- lsus.indices) memReqArb.io.in(i) <> lsus(i).mem.req
    l1_dcache.upper.req <> memReqArb.io.out

    val memRespQueue = Module(new Queue(UInt(log2Ceil(lsus.length).W), p(ROBSize)))
    memRespQueue.io.enq.valid := memReqArb.io.out.fire
    memRespQueue.io.enq.bits  := memReqArb.io.chosen
    memRespQueue.io.deq.ready := l1_dcache.upper.resp.fire

    val memTarget = memRespQueue.io.deq.bits
    for (i <- lsus.indices) {
      lsus(i).mem.resp.valid := l1_dcache.upper.resp.valid && memRespQueue.io.deq.valid && (memTarget === i.U)
      lsus(i).mem.resp.bits  := l1_dcache.upper.resp.bits
    }

    l1_dcache.upper.resp.ready := false.B
    for (i <- lsus.indices)
      when(memRespQueue.io.deq.valid && memTarget === i.U) {
        l1_dcache.upper.resp.ready := lsus(i).mem.resp.ready
      }

    val mmioReqArb = Module(new RRArbiter(chiselTypeOf(lsus(0).mmio.req.bits), lsus.length))
    for (i <- lsus.indices) mmioReqArb.io.in(i) <> lsus(i).mmio.req
    mmio.req <> mmioReqArb.io.out

    val mmioRespQueue = Module(new Queue(UInt(log2Ceil(lsus.length).W), p(ROBSize)))
    mmioRespQueue.io.enq.valid := mmioReqArb.io.out.fire
    mmioRespQueue.io.enq.bits  := mmioReqArb.io.chosen
    mmioRespQueue.io.deq.ready := mmio.resp.fire

    val mmioTarget = mmioRespQueue.io.deq.bits
    for (i <- lsus.indices) {
      lsus(i).mmio.resp.valid := mmio.resp.valid && mmioRespQueue.io.deq.valid && (mmioTarget === i.U)
      lsus(i).mmio.resp.bits  := mmio.resp.bits
    }

    mmio.resp.ready := false.B
    for (i <- lsus.indices)
      when(mmioRespQueue.io.deq.valid && mmioTarget === i.U) {
        mmio.resp.ready := lsus(i).mmio.resp.ready
      }
  }

  dmem <> l1_dcache.lower

  csrs.foreach { csr =>
    csr.arch_pc := Mux(rob.io.empty, ifu.if_pc(0), rob.io.commit(0).pc)
  }

  val commit_pops = rob.io.commit.map(_.pop)
  val commit_fire = commit_pops.reduce(_ || _)

  val is_flush = Wire(Vec(p(IssueWidth), Bool()))
  for (w <- 0 until p(IssueWidth))
    is_flush(w) := rob.io.commit(w).pop && rob.io.commit(w).flush_pipeline

  val commit_flush_pipeline = is_flush.reduce(_ || _)
  val commit_flush_target   = Mux1H(is_flush.zipWithIndex.map { case (f, w) => f -> rob.io.commit(w).flush_target })

  val async_trap_req = if (csrs.nonEmpty) csrs.map(c => c.trap_request && !c.is_busy).foldLeft(false.B)(_ || _) else false.B
  val async_trap_tgt = if (csrs.nonEmpty) Mux1H(csrs.map(c => (c.trap_request && !c.is_busy) -> c.trap_target)) else 0.U(p(XLen).W)

  val global_flush = commit_flush_pipeline || async_trap_req
  val redirect_pc  = Mux(async_trap_req, async_trap_tgt, commit_flush_target)

  imem.req <> l1_icache.lower.req
  imem.resp.ready                := l1_icache.lower.resp.ready
  l1_icache.lower.resp.valid     := imem.resp.valid
  l1_icache.lower.resp.bits.hit  := imem.resp.bits.hit
  l1_icache.lower.resp.bits.data := imem.resp.bits.data.asTypeOf(chiselTypeOf(l1_icache.lower.resp.bits.data))

  ifu.mem <> l1_icache.upper

  bpu.query_pc      := ifu.fetch_pc
  ifu.bpu_taken_in  := bpu.taken
  ifu.bpu_target_in := bpu.target

  ifu.take_trap     := global_flush
  ifu.trap_target   := redirect_pc
  ifu.bru_taken     := false.B
  ifu.bru_target    := 0.U
  ifu.bru_not_taken := false.B
  ifu.bru_branch_pc := 0.U

  val aluIds  = p(FunctionalUnits).zipWithIndex.filter(_._1.`type` == FUNCTIONAL_UNIT_TYPE_ALU).map(_._2.U)
  val multIds = p(FunctionalUnits).zipWithIndex.filter(_._1.`type` == FUNCTIONAL_UNIT_TYPE_MULT).map(_._2.U)
  val lsuIds  = p(FunctionalUnits).zipWithIndex.filter(_._1.`type` == FUNCTIONAL_UNIT_TYPE_LSU).map(_._2.U)
  val bruIds  = p(FunctionalUnits).zipWithIndex.filter(_._1.`type` == FUNCTIONAL_UNIT_TYPE_BRU).map(_._2.U)
  val csrIds  = p(FunctionalUnits).zipWithIndex.filter(_._1.`type` == FUNCTIONAL_UNIT_TYPE_CSR).map(_._2.U)

  val rs1s = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rs2s = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rds  = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))

  val is_bubble = Wire(Vec(p(IssueWidth), Bool()))
  val is_csr    = Wire(Vec(p(IssueWidth), Bool()))
  val is_lsu    = Wire(Vec(p(IssueWidth), Bool()))
  val hazard    = Wire(Vec(p(IssueWidth), Bool()))

  var csr_active = !rob.io.empty

  val uncompleted_lsus = RegInit(0.U(log2Ceil(p(ROBSize) + 1).W))

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

    is_bubble(w) := ifu.if_instr(w) === p(Bubble).value.U(p(ILen).W)
    is_csr(w)    := decoders(w).decoded.csr || decoders(w).decoded.ret
    is_lsu(w)    := decoders(w).decoded.lsu

    val csr_haz = is_csr(w) && (csr_active || w.U > 0.U)
    val lsu_haz = is_lsu(w) && (uncompleted_lsus > 0.U || w.U > 0.U)

    hazard(w) := csr_haz || lsu_haz

    if (w > 0) {
      val next_csr = WireDefault(csr_active)
      when(is_csr(w) && !is_bubble(w))(next_csr := true.B)
      csr_active = next_csr
    }
  }

  val wants_to_issue = Wire(Vec(p(IssueWidth), Bool()))
  val inst_type      = Wire(Vec(p(IssueWidth), UInt(3.W)))

  val TYPE_ALU  = 0.U(3.W)
  val TYPE_LSU  = 1.U(3.W)
  val TYPE_MULT = 2.U(3.W)
  val TYPE_BRU  = 3.U(3.W)
  val TYPE_CSR  = 4.U(3.W)

  val kill_mask = Wire(Vec(p(IssueWidth), Bool()))
  kill_mask(0)   := false.B
  for (w <- 1 until p(IssueWidth))
    kill_mask(w) := kill_mask(w - 1) || (ifu.if_valid(w - 1) && ifu.if_bpu_pred_taken(w - 1))

  for (w <- 0 until p(IssueWidth)) {
    wants_to_issue(w) := ifu.if_valid(w) && !is_bubble(w) && !hazard(w) && !global_flush && !kill_mask(w)

    inst_type(w) := MuxCase(
      TYPE_ALU,
      Seq(
        is_lsu(w)                                            -> TYPE_LSU,
        decoders(w).decoded.mult_en                          -> TYPE_MULT,
        decoders(w).decoded.branch                           -> TYPE_BRU,
        (decoders(w).decoded.csr || decoders(w).decoded.ret) -> TYPE_CSR
      )
    )
  }

  val intra_hazard = Wire(Vec(p(IssueWidth), Bool()))
  intra_hazard(0) := false.B
  for (w <- 1 until p(IssueWidth)) {
    val conflicts = (0 until w).map { v =>
      val rd_v       = Mux(decoders(v).decoded.regwrite, rds(v), 0.U)
      val is_issuing = wants_to_issue(v)
      is_issuing && rd_v =/= 0.U && (rs1s(w) === rd_v || rs2s(w) === rd_v)
    }
    intra_hazard(w) := conflicts.reduce(_ || _)
  }

  val struct_hazard = Wire(Vec(p(IssueWidth), Bool()))
  val target_fu_id  = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(FunctionalUnits).size).W)))

  for (w <- 0 until p(IssueWidth)) {
    val alu_used  = PopCount((0 until w).map(i => wants_to_issue(i) && !intra_hazard(i) && inst_type(i) === TYPE_ALU && !struct_hazard(i)))
    val lsu_used  = PopCount((0 until w).map(i => wants_to_issue(i) && !intra_hazard(i) && inst_type(i) === TYPE_LSU && !struct_hazard(i)))
    val mult_used = PopCount((0 until w).map(i => wants_to_issue(i) && !intra_hazard(i) && inst_type(i) === TYPE_MULT && !struct_hazard(i)))
    val bru_used  = PopCount((0 until w).map(i => wants_to_issue(i) && !intra_hazard(i) && inst_type(i) === TYPE_BRU && !struct_hazard(i)))
    val csr_used  = PopCount((0 until w).map(i => wants_to_issue(i) && !intra_hazard(i) && inst_type(i) === TYPE_CSR && !struct_hazard(i)))

    def getId(used: UInt, ids: Seq[UInt]): UInt =
      if (ids.isEmpty) 0.U
      else MuxLookup(used, ids.head)(ids.zipWithIndex.map { case (id, idx) => idx.U -> id })

    struct_hazard(w) := MuxCase(
      false.B,
      Seq(
        (inst_type(w) === TYPE_ALU)  -> (alu_used >= aluIds.length.U),
        (inst_type(w) === TYPE_LSU)  -> (lsu_used >= lsuIds.length.U),
        (inst_type(w) === TYPE_MULT) -> (mult_used >= multIds.length.U),
        (inst_type(w) === TYPE_BRU)  -> (bru_used >= bruIds.length.U),
        (inst_type(w) === TYPE_CSR)  -> (csr_used >= csrIds.length.U)
      )
    )

    target_fu_id(w) := MuxCase(
      getId(alu_used, aluIds),
      Seq(
        (inst_type(w) === TYPE_LSU)  -> getId(lsu_used, lsuIds),
        (inst_type(w) === TYPE_MULT) -> getId(mult_used, multIds),
        (inst_type(w) === TYPE_BRU)  -> getId(bru_used, bruIds),
        (inst_type(w) === TYPE_CSR)  -> getId(csr_used, csrIds)
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
      ifu_fire(w)   := ifu.if_valid(w) && (is_bubble(w) || global_flush || fire(w))
    } else {
      fire(w)       := can_consume && fire(w - 1)
      lane_valid(w) := valid_req && dis_ready(w) && fire(w - 1)
      ifu_fire(w)   := ifu.if_valid(w) && (is_bubble(w) || global_flush || fire(w)) && ifu_fire(w - 1)
    }
  }

  ifu.dispatch_fire := ifu_fire

  val lsu_dispatched = PopCount((0 until p(IssueWidth)).map(w => lane_valid(w) && is_lsu(w)))
  val lsu_wb         = PopCount(lsuIds.map(id => rob.io.wb(id).valid))

  when(global_flush) {
    uncompleted_lsus := 0.U
  }.otherwise {
    uncompleted_lsus := uncompleted_lsus + lsu_dispatched - lsu_wb
  }

  val bpu_update_valid  = WireDefault(false.B)
  val bpu_update_pc     = WireDefault(0.U(p(XLen).W))
  val bpu_update_target = WireDefault(0.U(p(XLen).W))
  val bpu_update_taken  = WireDefault(false.B)

  for (w <- p(IssueWidth) - 1 to 0 by -1)
    when(rob.io.commit(w).pop && rob.io.commit(w).is_branch) {
      bpu_update_valid  := true.B
      bpu_update_pc     := rob.io.commit(w).pc
      bpu_update_target := rob.io.commit(w).bpu_actual_target
      bpu_update_taken  := rob.io.commit(w).bpu_actual_taken
    }

  bpu.update.valid  := bpu_update_valid
  bpu.update.pc     := bpu_update_pc
  bpu.update.target := bpu_update_target
  bpu.update.taken  := bpu_update_taken

  ifu.lsu_busy := false.B

  for (w <- 0 until p(IssueWidth)) {
    rob.io.enq(w).valid           := lane_valid(w)
    rob.io.enq(w).pc              := ifu.if_pc(w)
    rob.io.enq(w).instr           := ifu.if_instr(w)
    rob.io.enq(w).rd              := Mux(decoders(w).decoded.regwrite, rds(w), 0.U)
    rob.io.enq(w).pd              := 0.U
    rob.io.enq(w).old_pd          := 0.U
    rob.io.enq(w).is_branch       := decoders(w).decoded.branch
    rob.io.enq(w).is_lsu          := is_lsu(w)
    rob.io.enq(w).bpu_pred_taken  := ifu.if_bpu_pred_taken(w)
    rob.io.enq(w).bpu_pred_target := ifu.if_bpu_pred_target(w)

    val rs1_bypassed = Mux(rob.io.rs1_bypass(w).valid, rob.io.rs1_bypass(w).data, regfile.rs1_data(w))
    val rs2_bypassed = Mux(rob.io.rs2_bypass(w).valid, rob.io.rs2_bypass(w).data, regfile.rs2_data(w))

    val dis = scheduler.dis_reqs(w)
    dis.valid         := lane_valid(w)
    dis.bits.pc       := ifu.if_pc(w)
    dis.bits.instr    := ifu.if_instr(w)
    dis.bits.fu_id    := target_fu_id(w)
    dis.bits.rs1      := rs1s(w)
    dis.bits.rs2      := rs2s(w)
    dis.bits.rd       := Mux(decoders(w).decoded.regwrite, rds(w), 0.U)
    dis.bits.rs1_data := rs1_bypassed
    dis.bits.rs2_data := rs2_bypassed
    dis.bits.rob_tag  := rob.io.enq(w).rob_tag
  }

  scheduler.flush := global_flush
  rob.io.flush    := global_flush

  for ((fu, i) <- fus.zipWithIndex) {
    fu.io.req <> scheduler.fu_reqs(i)
    fu.io.resp.ready := true.B
    fu.io.flush      := global_flush

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

    regfile.write_en(w)   := rob.io.commit(w).pop && (rob.io.commit(w).rd =/= 0.U)
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
  val debug_instret       = IO(Output(Bool()))
  val debug_pc            = IO(Output(UInt(p(XLen).W)))
  val debug_instr         = IO(Output(UInt(p(ILen).W)))
  val debug_reg_we        = IO(Output(Bool()))
  val debug_reg_addr      = IO(Output(UInt(log2Ceil(p(NumArchRegs)).W)))
  val debug_reg_data      = IO(Output(UInt(p(XLen).W)))

  debug_cycle_count   := cycle_count
  debug_instret_count := instret_count

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
}
