package arch.core

import ifu._
import decoder._
import imm._
import bru._
import regfile._
import bpu._
import arch.core.pipeline._
import arch.core.csr.CoreInterruptIO
import arch.configs._
import arch.core.ooo._
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

  // Scheduler Initialization
  val scheduler = SchedulerFactory()
  val numFUs    = p(FunctionalUnits).size

  val alu_fu  = Module(new AluFU)
  val mult_fu = Module(new MultFU)
  val lsu_fu  = Module(new LsuFU)
  val csr_fu  = Module(new CsrFU)

  l1_dcache.upper <> lsu_fu.mem
  mmio <> lsu_fu.mmio
  dmem <> l1_dcache.lower

  val fuMap = p(FunctionalUnits).map(_.name).map {
    case "ALU_0"  => alu_fu.io
    case "MULT_0" => mult_fu.io
    case "LSU_0"  => lsu_fu.io
    case "CSR"    => csr_fu.io
  }

  for (i <- 0 until numFUs)
    fuMap(i).req <> scheduler.fu_reqs(i)

  // Fetch Stage
  imem <> l1_icache.lower
  ifu.mem <> l1_icache.upper

  bpu.query_pc      := ifu.fetch_pc
  ifu.bpu_taken_in  := bpu.taken
  ifu.bpu_target_in := bpu.target

  val if_id = PipelineStageBuilder("if_id")
    .field("instr", p(ILen), p(Bubble).value.toLong)
    .field("pc", p(XLen))
    .field("bpu_pred_taken", 1)
    .field("bpu_pred_target", p(XLen))
    .build()

  // Decode & Dispatch Stage
  decoder.instr   := if_id("instr")
  imm_gen.instr   := if_id("instr")
  imm_gen.immType := decoder.decoded.imm_type

  val rs1 = regfile_utils.getRs1(if_id("instr"))
  val rs2 = regfile_utils.getRs2(if_id("instr"))
  val rd  = regfile_utils.getRd(if_id("instr"))

  regfile.rs1_preg := rs1
  regfile.rs2_preg := rs2

  val take_trap   = csr_fu.trap_request
  val trap_target = Mux(take_trap, csr_fu.trap_target, csr_fu.trap_ret_tgt)

  val is_bubble = if_id("instr") === p(Bubble).value.U(p(ILen).W)
  val sb_ready  = scheduler.dis_reqs(0).ready

  // Only fire events if the instruction successfully dispatches to the scoreboard
  val dispatch_fire = !is_bubble && !take_trap && sb_ready

  // Branch Unit Logic
  bru.en     := decoder.decoded.branch && dispatch_fire
  bru.pc     := if_id("pc")
  bru.src1   := regfile.rs1_data
  bru.src2   := regfile.rs2_data
  bru.imm    := imm_gen.imm
  bru.brType := decoder.decoded.br_type

  bpu.update.valid  := bru.en
  bpu.update.pc     := if_id("pc")
  bpu.update.target := bru.target
  bpu.update.taken  := bru.taken

  val bpu_correct_taken        = if_id("bpu_pred_taken").asBool && (bru.target === if_id("bpu_pred_target"))
  val bru_mispredict_taken     = bru.taken && !bpu_correct_taken
  val bru_mispredict_not_taken = bru.en && !bru.taken && if_id("bpu_pred_taken").asBool

  ifu.bru_taken     := bru_mispredict_taken
  ifu.bru_target    := bru.target
  ifu.bru_not_taken := bru_mispredict_not_taken
  ifu.bru_branch_pc := if_id("pc")

  val take_trap_ret = decoder.decoded.ret && dispatch_fire
  ifu.take_trap   := take_trap || take_trap_ret
  ifu.trap_target := trap_target

  ifu.id_ex_stall     := !sb_ready
  ifu.load_use_hazard := false.B
  ifu.lsu_busy        := false.B

  if_id.stall := ifu.if_id_stall || !sb_ready
  if_id.flush := ifu.if_id_flush || take_trap || take_trap_ret

  if_id.drive("instr", ifu.if_instr)
  if_id.drive("pc", ifu.if_pc)
  if_id.drive("bpu_pred_taken", ifu.if_bpu_pred_taken)
  if_id.drive("bpu_pred_target", ifu.if_bpu_pred_target)

  // Scoreboard Dispatch Array
  val fuNames = p(FunctionalUnits).map(_.name)
  val aluId   = fuNames.indexOf("ALU_0").U
  val multId  = fuNames.indexOf("MULT_0").U
  val lsuId   = fuNames.indexOf("LSU_0").U
  val csrId   = fuNames.indexOf("CSR").U

  val target_fu_id = MuxCase(
    aluId,
    Seq(
      decoder.decoded.lsu     -> lsuId,
      decoder.decoded.mult_en -> multId,
      decoder.decoded.csr     -> csrId
    )
  )

  val dis0 = scheduler.dis_reqs(0)
  dis0.valid         := !is_bubble && !take_trap
  dis0.bits.pc       := if_id("pc")
  dis0.bits.instr    := if_id("instr")
  dis0.bits.fu_id    := target_fu_id
  dis0.bits.rs1      := rs1
  dis0.bits.rs2      := rs2
  dis0.bits.rd       := Mux(decoder.decoded.regwrite, rd, 0.U)
  dis0.bits.rs1_data := regfile.rs1_data
  dis0.bits.rs2_data := regfile.rs2_data
  dis0.bits.rob_tag  := 0.U

  for (w <- 1 until p(IssueWidth)) {
    scheduler.dis_reqs(w).valid := false.B
    scheduler.dis_reqs(w).bits  := 0.U.asTypeOf(new MicroOp)
  }

  scheduler.flush := take_trap || take_trap_ret

  // Common Writeback Arbiter
  val wb_arbiter = Module(new RRArbiter(new FunctionalUnitResp, numFUs))
  for (i <- 0 until numFUs) {
    wb_arbiter.io.in(i) <> fuMap(i).resp
    fuMap(i).flush := take_trap || take_trap_ret

    scheduler.fu_done(i).valid := wb_arbiter.io.in(i).fire
    scheduler.fu_done(i).bits  := wb_arbiter.io.in(i).bits
  }

  wb_arbiter.io.out.ready := true.B
  val wb_resp = wb_arbiter.io.out.bits
  val wb_fire = wb_arbiter.io.out.fire

  regfile.write_en   := wb_fire && (wb_resp.rd =/= 0.U)
  regfile.write_preg := wb_resp.rd
  regfile.write_data := wb_resp.result

  // System Registers / Cycle Counters
  val cycle_count   = RegInit(0.U(64.W))
  val instret_count = RegInit(0.U(64.W))
  cycle_count   := cycle_count + 1.U
  instret_count := instret_count + Mux(wb_fire, 1.U, 0.U)

  csr_fu.cycle   := cycle_count
  csr_fu.instret := instret_count
  csr_fu.irq     := irq

  // Debug IOs
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
  debug_instret       := wb_fire
  debug_pc            := wb_resp.pc
  debug_instr         := wb_resp.instr
  debug_reg_we        := regfile.write_en
  debug_reg_addr      := regfile.write_preg
  debug_reg_data      := regfile.write_data

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
