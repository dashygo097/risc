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
import chisel3.util.{ log2Ceil, MuxCase, Mux1H, PopCount }

class RiscCore(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_cpu"

  val imem = IO(new CacheReadOnlyIO(Vec(p(L1ICacheLineSize) / (p(XLen) / 8), UInt(p(XLen).W)), p(ILen)))
  val dmem = IO(new CacheIO(Vec(p(L1DCacheLineSize) / (p(XLen) / 8), UInt(p(XLen).W)), p(XLen)))
  val mmio = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))
  val irq  = IO(new CoreInterruptIO)

  val bpu       = Module(new Bpu)
  val ifu       = Module(new Ifu)
  val decoders  = Seq.fill(p(IssueWidth))(Module(new Decoder))
  val regfile   = Module(new Regfile)
  val imm_gens  = Seq.fill(p(IssueWidth))(Module(new ImmGen))
  val l1_icache = Module(new SetAssociativeCacheReadOnly(UInt(p(XLen).W), p(XLen), p(L1ICacheLineSize) / (p(XLen) / 8), p(L1ICacheSets), p(L1ICacheWays), p(L1ICacheReplPolicy)))
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

  val lsu_fu = lsus.headOption.getOrElse(throw new Exception("LSU Unit is mandatory but missing from configuration!"))

  l1_dcache.upper <> lsu_fu.mem
  mmio <> lsu_fu.mmio
  dmem <> l1_dcache.lower

  csrs.foreach { csr =>
    csr.arch_pc := Mux(rob.io.empty, ifu.if_pc(0), rob.io.commit(0).pc)
  }

  val commit_pops = rob.io.commit.map(_.pop)
  val commit_fire = commit_pops.reduce(_ || _)

  val commit_flush_pipeline = rob.io.commit.map(c => c.pop && c.flush_pipeline).reduce(_ || _)
  val commit_flush_target   = Mux1H(rob.io.commit.map(c => (c.pop && c.flush_pipeline) -> c.flush_target))

  val async_trap_req = if (csrs.nonEmpty) csrs.map(c => c.trap_request && !c.is_busy).foldLeft(false.B)(_ || _) else false.B
  val async_trap_tgt = if (csrs.nonEmpty) Mux1H(csrs.map(c => (c.trap_request && !c.is_busy) -> c.trap_target)) else 0.U(p(XLen).W)

  val global_flush = commit_flush_pipeline || async_trap_req
  val redirect_pc  = Mux(async_trap_req, async_trap_tgt, commit_flush_target)

  imem <> l1_icache.lower
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

  val rs1s = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rs2s = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rds  = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))

  val is_bubble = Wire(Vec(p(IssueWidth), Bool()))
  val is_csr    = Wire(Vec(p(IssueWidth), Bool()))
  val hazard    = Wire(Vec(p(IssueWidth), Bool()))

  var csr_active = !rob.io.empty

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
    hazard(w)    := is_csr(w) && (csr_active || w.U > 0.U)

    if (w > 0) when(is_csr(w)) { csr_active = true.B }
  }

  val valid_reqs = Wire(Vec(p(IssueWidth), Bool()))
  val dis_ready  = Wire(Vec(p(IssueWidth), Bool()))
  val fire       = Wire(Vec(p(IssueWidth), Bool()))
  val lane_valid = Wire(Vec(p(IssueWidth), Bool()))
  val ifu_fire   = Wire(Vec(p(IssueWidth), Bool()))

  for (w <- 0 until p(IssueWidth)) {
    valid_reqs(w) := ifu.if_valid(w) && !is_bubble(w) && !hazard(w) && !global_flush
    dis_ready(w)  := scheduler.dis_reqs(w).ready && rob.io.enq(w).ready

    if (w == 0) {
      lane_valid(w) := valid_reqs(w)
      fire(w)       := lane_valid(w) && dis_ready(w)
      ifu_fire(w)   := ifu.if_valid(w) && (is_bubble(w) || global_flush || fire(w))
    } else {
      lane_valid(w) := valid_reqs(w) && fire(w - 1)
      fire(w)       := lane_valid(w) && dis_ready(w)
      ifu_fire(w)   := ifu.if_valid(w) && (is_bubble(w) || global_flush || fire(w)) && ifu_fire(w - 1)
    }
  }

  ifu.dispatch_fire := ifu_fire

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

  def getFuId(t: proto.FunctionalUnitType): UInt =
    math.max(0, p(FunctionalUnits).indexWhere(_.`type` == t)).U

  for (w <- 0 until p(IssueWidth)) {
    val target_fu_id = MuxCase(
      getFuId(FUNCTIONAL_UNIT_TYPE_ALU),
      Seq(
        decoders(w).decoded.lsu                              -> getFuId(FUNCTIONAL_UNIT_TYPE_LSU),
        decoders(w).decoded.mult_en                          -> getFuId(FUNCTIONAL_UNIT_TYPE_MULT),
        (decoders(w).decoded.csr || decoders(w).decoded.ret) -> getFuId(FUNCTIONAL_UNIT_TYPE_CSR),
        decoders(w).decoded.branch                           -> getFuId(FUNCTIONAL_UNIT_TYPE_BRU)
      )
    )

    rob.io.enq(w).valid           := fire(w)
    rob.io.enq(w).pc              := ifu.if_pc(w)
    rob.io.enq(w).instr           := ifu.if_instr(w)
    rob.io.enq(w).rd              := Mux(decoders(w).decoded.regwrite, rds(w), 0.U)
    rob.io.enq(w).pd              := 0.U
    rob.io.enq(w).old_pd          := 0.U
    rob.io.enq(w).is_branch       := decoders(w).decoded.branch
    rob.io.enq(w).bpu_pred_taken  := ifu.if_bpu_pred_taken(w)
    rob.io.enq(w).bpu_pred_target := ifu.if_bpu_pred_target(w)

    val rs1_bypassed = Mux(rob.io.rs1_bypass(w).valid, rob.io.rs1_bypass(w).data, regfile.rs1_data(w))
    val rs2_bypassed = Mux(rob.io.rs2_bypass(w).valid, rob.io.rs2_bypass(w).data, regfile.rs2_data(w))

    val dis = scheduler.dis_reqs(w)
    dis.valid         := fire(w)
    dis.bits.pc       := ifu.if_pc(w)
    dis.bits.instr    := ifu.if_instr(w)
    dis.bits.fu_id    := target_fu_id
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
