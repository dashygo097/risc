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
import pipeline._
import ooo._
import arch.configs._
import arch.configs.proto.FunctionalUnitType._
import vopts.mem.cache.{ CacheIO, CacheReadOnlyIO, SetAssociativeCache, SetAssociativeCacheReadOnly }
import chisel3._
import chisel3.util.{ log2Ceil, MuxCase, RRArbiter }

class RiscCore(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_cpu"

  val imem = IO(new CacheReadOnlyIO(Vec(p(L1ICacheLineSize) / (p(XLen) / 8), UInt(p(XLen).W)), p(ILen)))
  val dmem = IO(new CacheIO(Vec(p(L1DCacheLineSize) / (p(XLen) / 8), UInt(p(XLen).W)), p(XLen)))
  val mmio = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))
  val irq  = IO(new CoreInterruptIO)

  val bpu       = Module(new Bpu)
  val ifu       = Module(new Ifu)
  val decoder   = Module(new Decoder)
  val bru       = Module(new Bru)
  val regfile   = Module(new Regfile)
  val imm_gen   = Module(new ImmGen)
  val l1_icache = Module(new SetAssociativeCacheReadOnly(UInt(p(XLen).W), p(XLen), p(L1ICacheLineSize) / (p(XLen) / 8), p(L1ICacheSets), p(L1ICacheWays), p(L1ICacheReplPolicy)))
  val l1_dcache = Module(new SetAssociativeCache(UInt(p(XLen).W), p(XLen), p(L1DCacheLineSize) / (p(XLen) / 8), p(L1DCacheSets), p(L1DCacheWays), p(L1DCacheReplPolicy)))

  val regfile_utils = RegfileUtilitiesFactory.getOrThrow(p(ISA).name)

  val scheduler = Scheduler()
  val rob       = Module(new ReorderBuffer)

  var lsu_module: Option[LsuFU] = None
  var csr_module: Option[CsrFU] = None

  val fuMap = p(FunctionalUnits).map { fuDesc =>
    fuDesc.`type` match {
      case FUNCTIONAL_UNIT_TYPE_ALU  => Module(new AluFU).io
      case FUNCTIONAL_UNIT_TYPE_MULT => Module(new MultFU).io
      case FUNCTIONAL_UNIT_TYPE_LSU  =>
        val lsu = Module(new LsuFU)
        lsu_module = Some(lsu)
        lsu.io
      case FUNCTIONAL_UNIT_TYPE_CSR  =>
        val csr = Module(new CsrFU)
        csr_module = Some(csr)
        csr.io
      case _                         => throw new Exception(s"Unknown FunctionalUnitType: ${fuDesc.`type`}")
    }
  }

  val lsu_fu = lsu_module.getOrElse(throw new Exception("LSU Unit is mandatory but missing from configuration!"))

  l1_dcache.upper <> lsu_fu.mem
  mmio <> lsu_fu.mmio
  dmem <> l1_dcache.lower

  for (i <- 0 until p(FunctionalUnits).size)
    fuMap(i).req <> scheduler.fu_reqs(i)

  val frontend = PipelineStageBuilder("frontend")
    .field("instr", p(ILen), p(Bubble).value.toLong)
    .field("pc", p(XLen))
    .field("bpu_pred_taken", 1)
    .field("bpu_pred_target", p(XLen))
    .build()

  csr_module.foreach { csr =>
    csr.arch_pc := Mux(rob.io.empty, frontend("pc"), rob.io.commit_pc)
  }

  val commit_fire = rob.io.commit_valid

  val async_trap_req = csr_module.map(c => c.trap_request && !c.is_busy).getOrElse(false.B)
  val async_trap_tgt = csr_module.map(_.trap_target).getOrElse(0.U)

  val global_flush = (commit_fire && rob.io.commit_flush_pipeline) || async_trap_req
  val redirect_pc  = Mux(async_trap_req, async_trap_tgt, rob.io.commit_flush_target)

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

  decoder.instr   := frontend("instr")
  imm_gen.instr   := frontend("instr")
  imm_gen.immType := decoder.decoded.imm_type

  val rs1 = regfile_utils.getRs1(frontend("instr"))
  val rs2 = regfile_utils.getRs2(frontend("instr"))
  val rd  = regfile_utils.getRd(frontend("instr"))

  regfile.rs1_preg := rs1
  regfile.rs2_preg := rs2

  rob.io.rs1_addr := rs1
  rob.io.rs2_addr := rs2
  val rs1_bypassed = Mux(rob.io.rs1_bypass_valid, rob.io.rs1_bypass_data, regfile.rs1_data)
  val rs2_bypassed = Mux(rob.io.rs2_bypass_valid, rob.io.rs2_bypass_data, regfile.rs2_data)

  val is_bubble = frontend("instr") === p(Bubble).value.U(p(ILen).W)

  val branch_hazard = decoder.decoded.branch && (rob.io.rs1_pending || rob.io.rs2_pending)
  val csr_hazard    = (decoder.decoded.csr || decoder.decoded.ret) && !rob.io.empty

  val sb_ready  = is_bubble || scheduler.dis_reqs(0).ready
  val rob_ready = is_bubble || rob.io.enq_ready

  val dispatch_fire = !is_bubble && scheduler.dis_reqs(0).ready && rob.io.enq_ready && !branch_hazard && !csr_hazard

  bru.en     := decoder.decoded.branch && dispatch_fire
  bru.pc     := frontend("pc")
  bru.src1   := rs1_bypassed
  bru.src2   := rs2_bypassed
  bru.imm    := imm_gen.imm
  bru.brType := decoder.decoded.br_type

  bpu.update.valid  := bru.en
  bpu.update.pc     := frontend("pc")
  bpu.update.target := bru.target
  bpu.update.taken  := bru.taken

  val bpu_correct_taken        = frontend("bpu_pred_taken").asBool && (bru.target === frontend("bpu_pred_target"))
  val bru_mispredict_taken     = bru.taken && !bpu_correct_taken
  val bru_mispredict_not_taken = bru.en && !bru.taken && frontend("bpu_pred_taken").asBool

  when(bru_mispredict_taken || bru_mispredict_not_taken) {
    ifu.bru_taken     := bru_mispredict_taken
    ifu.bru_target    := bru.target
    ifu.bru_not_taken := bru_mispredict_not_taken
    ifu.bru_branch_pc := frontend("pc")
  }

  ifu.id_ex_stall     := !sb_ready || !rob_ready || branch_hazard || csr_hazard
  ifu.load_use_hazard := false.B
  ifu.lsu_busy        := false.B

  frontend.stall := ifu.fronend_stall || !sb_ready || !rob_ready || branch_hazard || csr_hazard
  frontend.flush := ifu.fronend_flush || global_flush || bru_mispredict_taken || bru_mispredict_not_taken

  frontend.drive("instr", ifu.if_instr)
  frontend.drive("pc", ifu.if_pc)
  frontend.drive("bpu_pred_taken", ifu.if_bpu_pred_taken)
  frontend.drive("bpu_pred_target", ifu.if_bpu_pred_target)

  val fuTypes = p(FunctionalUnits).map(_.`type`)

  val aluId  = math.max(0, fuTypes.indexOf(FUNCTIONAL_UNIT_TYPE_ALU)).U
  val lsuId  = math.max(0, fuTypes.indexOf(FUNCTIONAL_UNIT_TYPE_LSU)).U
  val multId = math.max(0, fuTypes.indexOf(FUNCTIONAL_UNIT_TYPE_MULT)).U
  val csrId  = math.max(0, fuTypes.indexOf(FUNCTIONAL_UNIT_TYPE_CSR)).U

  val target_fu_id = MuxCase(
    aluId,
    Seq(
      decoder.decoded.lsu                          -> lsuId,
      decoder.decoded.mult_en                      -> multId,
      (decoder.decoded.csr || decoder.decoded.ret) -> csrId
    )
  )

  rob.io.enq_valid  := dispatch_fire
  rob.io.enq_pc     := frontend("pc")
  rob.io.enq_instr  := frontend("instr")
  rob.io.enq_rd     := Mux(decoder.decoded.regwrite, rd, 0.U)
  rob.io.enq_pd     := 0.U
  rob.io.enq_old_pd := 0.U

  val dis0 = scheduler.dis_reqs(0)
  dis0.valid         := dispatch_fire
  dis0.bits.pc       := frontend("pc")
  dis0.bits.instr    := frontend("instr")
  dis0.bits.fu_id    := target_fu_id
  dis0.bits.rs1      := rs1
  dis0.bits.rs2      := rs2
  dis0.bits.rd       := Mux(decoder.decoded.regwrite, rd, 0.U)
  dis0.bits.rs1_data := rs1_bypassed
  dis0.bits.rs2_data := rs2_bypassed
  dis0.bits.rob_tag  := rob.io.rob_tag

  for (w <- 1 until p(IssueWidth)) {
    scheduler.dis_reqs(w).valid := false.B
    scheduler.dis_reqs(w).bits  := 0.U.asTypeOf(new MicroOp)
  }

  scheduler.flush := global_flush
  rob.io.flush    := global_flush

  val wb_arbiter = Module(new RRArbiter(new FunctionalUnitResp, p(FunctionalUnits).size))
  for (i <- 0 until p(FunctionalUnits).size) {
    wb_arbiter.io.in(i) <> fuMap(i).resp
    fuMap(i).flush := global_flush

    scheduler.fu_done(i).valid := wb_arbiter.io.in(i).fire
    scheduler.fu_done(i).bits  := wb_arbiter.io.in(i).bits
  }

  wb_arbiter.io.out.ready := true.B
  val wb_resp = wb_arbiter.io.out.bits
  val wb_fire = wb_arbiter.io.out.fire

  val csrIdx    = p(FunctionalUnits).indexWhere(_.`type` == FUNCTIONAL_UNIT_TYPE_CSR)
  val is_csr_wb = if (csrIdx >= 0) wb_arbiter.io.in(csrIdx).fire else false.B

  rob.io.wb_valid          := wb_fire
  rob.io.wb_rob_tag        := wb_resp.rob_tag
  rob.io.wb_data           := wb_resp.result
  rob.io.wb_bru_mispredict := false.B
  rob.io.wb_bru_target     := 0.U
  rob.io.wb_trap_req       := csr_module.map(c => is_csr_wb && c.trap_request).getOrElse(false.B)
  rob.io.wb_trap_target    := csr_module.map(_.trap_target).getOrElse(0.U)
  rob.io.wb_trap_ret       := csr_module.map(c => is_csr_wb && c.trap_ret).getOrElse(false.B)
  rob.io.wb_trap_ret_tgt   := csr_module.map(_.trap_ret_tgt).getOrElse(0.U)

  rob.io.read_rob_tag := 0.U

  rob.io.commit_pop := commit_fire

  regfile.write_en   := commit_fire && (rob.io.commit_rd =/= 0.U)
  regfile.write_preg := rob.io.commit_rd
  regfile.write_data := rob.io.commit_data

  val cycle_count   = RegInit(0.U(64.W))
  val instret_count = RegInit(0.U(64.W))
  cycle_count   := cycle_count + 1.U
  instret_count := instret_count + Mux(commit_fire, 1.U, 0.U)

  csr_module.foreach { csr =>
    csr.cycle   := cycle_count
    csr.instret := instret_count

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

  debug_instret  := commit_fire
  debug_pc       := rob.io.commit_pc
  debug_instr    := rob.io.commit_instr
  debug_reg_we   := regfile.write_en
  debug_reg_addr := regfile.write_preg
  debug_reg_data := regfile.write_data

  val debug_branch_taken  = IO(Output(Bool()))
  val debug_branch_source = IO(Output(UInt(p(XLen).W)))
  val debug_branch_target = IO(Output(UInt(p(XLen).W)))

  debug_branch_taken  := bru.taken
  debug_branch_source := bru.pc
  debug_branch_target := bru.target

  val debug_l1_icache_access = IO(Output(Bool()))
  val debug_l1_icache_miss   = IO(Output(Bool()))
  val debug_l1_dcache_access = IO(Output(Bool()))
  val debug_l1_dcache_miss   = IO(Output(Bool()))

  debug_l1_icache_access := RegNext(l1_icache.upper.req.fire)
  debug_l1_icache_miss   := !l1_icache.upper.resp.bits.hit
  debug_l1_dcache_access := RegNext(l1_dcache.upper.req.fire)
  debug_l1_dcache_miss   := !l1_dcache.upper.resp.bits.hit
}
