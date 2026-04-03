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
import arch.configs._
import arch.core.ooo.{ FUInit, FURegistry, Scoreboard }
import mul._
import vopts.mem.cache.{ CacheIO, CacheReadOnlyIO, SetAssociativeCache, SetAssociativeCacheReadOnly }
import chisel3._
import chisel3.util.{ log2Ceil, MuxLookup, MuxCase }

class RiscCore(implicit p: Parameters) extends Module with AluConsts {
  override def desiredName: String = s"${p(ISA)}_cpu"

  val alu_utils     = AluUtilitiesFactory.getOrThrow(p(ISA))
  val bru_utils     = BruUtilitiesFactory.getOrThrow(p(ISA))
  val regfile_utils = RegfileUtilitiesFactory.getOrThrow(p(ISA))
  val lsu_utils     = LsuUtilitiesFactory.getOrThrow(p(ISA))
  val csr_utils     = CsrUtilitiesFactory.getOrThrow(p(ISA))

  private val FU_ALU = FUInit.ALU
  private val FU_MUL = FUInit.MUL
  private val FU_LSU = FUInit.LSU
  private val FU_CSR = FUInit.CSR

  val imem = IO(new CacheReadOnlyIO(Vec(p(L1ICacheLineSize) / (p(XLen) / 8), UInt(p(XLen).W)), p(ILen)))
  val dmem = IO(new CacheIO(Vec(p(L1DCacheLineSize) / (p(XLen) / 8), UInt(p(XLen).W)), p(XLen)))
  val mmio = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))
  val irq  = IO(new CoreInterruptIO)

  val bpu        = Module(new Bpu)
  val ifu        = Module(new Ifu)
  val decoder    = Module(new Decoder)
  val bru        = Module(new Bru)
  val regfile    = Module(new Regfile)
  val imm_gen    = Module(new ImmGen)
  val alu        = Module(new Alu)
  val lsu        = Module(new Lsu)
  val mul        = Module(new Mul)
  val scoreboard = Module(new Scoreboard)

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

  // Pipeline Registers
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
    .field("trap_ret", 1)
    .addFieldsWhen(p(EnableCSR))(
      Seq(
        PipelineField("csr", 1),
        PipelineField("csr_cmd", csr_utils.cmdWidth),
        PipelineField("csr_addr", csr_utils.addrWidth),
        PipelineField("csr_imm", p(XLen)),
      )
    )
    .build()

  val ex_wb = PipelineStageBuilder("ex_wb")
    .field("instr", p(ILen), p(Bubble).value.toLong)
    .field("pc", p(XLen))
    .field("rd", log2Ceil(p(NumArchRegs)))
    .field("regwrite", 1)
    .field("wb_data", p(XLen))
    .build()

  // Control Signals
  val load_use_hazard = Wire(Bool())
  val sb_stall        = Wire(Bool())

  val take_trap     = csrfile.map(_.trap_request).getOrElse(false.B)
  val trap_addr     = csrfile.map(_.trap_target).getOrElse(0.U(p(XLen).W))
  val take_trap_ret = id_ex("trap_ret").asBool && !id_ex.stall && !take_trap
  val trap_ret_addr = csrfile.map(_.trap_ret_target).getOrElse(0.U(p(XLen).W))

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

  ifu.take_trap   := take_trap || take_trap_ret
  ifu.trap_target := Mux(take_trap, trap_addr, trap_ret_addr)

  bpu.update.valid  := bru.en
  bpu.update.pc     := if_id("pc")
  bpu.update.target := bru.target
  bpu.update.taken  := bru.taken

  if_id.stall := ifu.if_id_stall || sb_stall
  if_id.flush := ifu.if_id_flush && !sb_stall

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

  def forwardData(rs: UInt, regData: UInt): UInt = {
    val ex_match      = id_ex("regwrite").asBool && (id_ex("rd") === rs) && (rs =/= 0.U)
    val wb_match      = ex_wb("regwrite").asBool && (ex_wb("rd") === rs) && (rs =/= 0.U)
    val ex_is_mul_fwd = id_ex("mul_en").asBool
    val ex_is_alu_fwd = id_ex("alu").asBool && !ex_is_mul_fwd &&
      !id_ex("lsu").asBool && !(if (p(EnableCSR)) id_ex("csr").asBool else false.B)
    MuxCase(
      regData,
      Seq(
        (ex_match && ex_is_alu_fwd)                                        -> alu.result,
        (ex_match && ex_is_mul_fwd && mul.io.done)                         -> mul.io.result,
        (ex_match && (if (p(EnableCSR)) id_ex("csr").asBool else false.B)) ->
          csrfile.map(_.rd).getOrElse(0.U),
        wb_match                                                           -> ex_wb("wb_data")
      )
    )
  }

  val id_rs1_data = forwardData(rs1, regfile.rs1_data)
  val id_rs2_data = forwardData(rs2, regfile.rs2_data)

  bru.en     := decoder.decoded.branch && !load_use_hazard && !sb_stall
  bru.pc     := if_id("pc")
  bru.src1   := id_rs1_data
  bru.src2   := id_rs2_data
  bru.imm    := imm_gen.imm
  bru.brType := decoder.decoded.br_type

  load_use_hazard := id_ex("lsu").asBool && lsu_utils.isRead(id_ex("lsu_cmd")) &&
    ((id_ex("rd") === rs1) || (id_ex("rd") === rs2)) &&
    id_ex("rd") =/= 0.U

  val mul_stall = WireDefault(false.B)

  val id_is_bubble = if_id("instr") === p(Bubble).value.U(p(ILen).W)

  // Scoreboard
  val id_is_csr = if (p(EnableCSR)) decoder.decoded.csr else false.B

  val sb_fu_id = MuxCase(
    FU_ALU.U,
    Seq(
      decoder.decoded.lsu    -> FU_LSU.U,
      decoder.decoded.mul_en -> FU_MUL.U,
      id_is_csr              -> FU_CSR.U
    )
  )

  val sb_issue_valid =
    !id_is_bubble &&
      !lsu.busy &&
      !mul_stall &&
      !take_trap &&
      !load_use_hazard &&
      decoder.decoded.regwrite

  scoreboard.io.issue_valid := sb_issue_valid
  scoreboard.io.issue_instr := if_id("instr")
  scoreboard.io.issue_rd    := rd
  scoreboard.io.issue_rs1   := rs1
  scoreboard.io.issue_rs2   := rs2
  scoreboard.io.issue_fu_id := sb_fu_id

  sb_stall := sb_issue_valid && !scoreboard.io.issue_ready

  // ID/EX Pipeline Control
  id_ex.stall := lsu.busy || mul_stall
  id_ex.flush :=
    ((load_use_hazard || sb_stall ||
      ((bru_mispredict_taken || bru_mispredict_not_taken) && !bru.jump)) &&
      !lsu.busy && !mul_stall) || take_trap

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
  id_ex.drive("trap_ret", decoder.decoded.ret)
  id_ex.driveOpt("csr", decoder.decoded.csr)
  id_ex.driveOpt("csr_cmd", decoder.decoded.csr_cmd)
  id_ex.driveOpt("csr_addr", csr_addr)
  id_ex.driveOpt("csr_imm", imm_gen.csr_imm)

  // EX Stage
  val ex_is_real = id_ex("instr") =/= p(Bubble).value.U(p(ILen).W)
  val ex_is_alu  = id_ex("alu").asBool && !id_ex("lsu").asBool &&
    !id_ex("mul_en").asBool && !(if (p(EnableCSR)) id_ex("csr").asBool else false.B)
  val ex_is_csr  = if (p(EnableCSR)) id_ex("csr").asBool else false.B
  val ex_is_lsu  = id_ex("lsu").asBool
  val ex_is_mul  = id_ex("mul_en").asBool

  val ex_rs1_data = id_ex("rs1_data")
  val ex_rs2_data = id_ex("rs2_data")

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

  // MUL
  val mul_req      = id_ex("mul_en").asBool
  val mul_inflight = RegInit(false.B)
  mul_stall := mul_req && !mul.io.done
  val mul_fire = mul_req && !mul_inflight && !mul.io.done

  when(id_ex.flush) {
    mul_inflight := false.B
  }.elsewhen(mul_fire) {
    mul_inflight := true.B
  }.elsewhen(mul.io.done) {
    mul_inflight := false.B
  }

  mul.io.en       := mul_fire
  mul.io.kill     := id_ex.flush
  mul.io.src1     := ex_rs1_data
  mul.io.src2     := ex_rs2_data
  mul.io.a_signed := id_ex("mul_a_signed").asBool
  mul.io.b_signed := id_ex("mul_b_signed").asBool
  mul.io.high     := id_ex("mul_high").asBool

  // CSR
  csrfile.foreach { csr =>
    csr.en       := id_ex("csr").asBool && !take_trap
    csr.trap_ret := id_ex("trap_ret").asBool && !take_trap
    csr.cmd      := id_ex("csr_cmd")
    csr.addr     := id_ex("csr_addr")
    csr.src      := ex_rs1_data
    csr.imm      := id_ex("csr_imm")
    csr.pc       := id_ex("pc")
  }

  // LSU
  val lsu_launched = RegInit(false.B)
  when(!lsu.busy) {
    lsu_launched := false.B
  }.elsewhen(ex_is_lsu && !lsu_launched) {
    lsu_launched := true.B
  }

  lsu.en    := ex_is_lsu && !lsu_launched
  lsu.cmd   := id_ex("lsu_cmd")
  lsu.addr  := alu.result
  lsu.wdata := ex_rs2_data

  val (_, pma_readable, pma_writable, pma_cacheable) = PmaChecker(alu.result)
  lsu.pma_readable  := pma_readable
  lsu.pma_writable  := pma_writable
  lsu.pma_cacheable := pma_cacheable

  l1_dcache.upper <> lsu.mem
  mmio <> lsu.mmio
  dmem <> l1_dcache.lower

  // EX/WB
  val lsu_was_busy   = RegNext(lsu.busy, false.B)
  val lsu_completing = lsu_was_busy && !lsu.busy

  val lsu_is_load = ex_is_lsu && lsu_utils.isRead(id_ex("lsu_cmd"))

  val ex_wb_data = MuxCase(
    alu.result,
    Seq(
      ex_is_csr   -> csrfile.map(_.rd).getOrElse(0.U),
      ex_is_mul   -> mul.io.result,
      lsu_is_load -> lsu.rdata
    )
  )

  val ex_completing = ex_is_real && !id_ex.stall

  ex_wb.stall := false.B
  ex_wb.flush := !ex_completing || take_trap

  ex_wb.drive("instr", id_ex("instr"))
  ex_wb.drive("pc", id_ex("pc"))
  ex_wb.drive("rd", id_ex("rd"))
  ex_wb.drive("regwrite", id_ex("regwrite"))
  ex_wb.drive("wb_data", ex_wb_data)

  // Scoreboard fu_done
  for (i <- 0 until FURegistry.numFUs) {
    scoreboard.io.fu_done(i) := false.B
    scoreboard.io.fu_rd(i)   := id_ex("rd")
  }

  scoreboard.io.fu_done(FU_ALU) := ex_is_alu && ex_is_real && !id_ex.stall
  scoreboard.io.fu_done(FU_CSR) := ex_is_csr && ex_is_real && !id_ex.stall
  scoreboard.io.fu_done(FU_MUL) := mul.io.done
  scoreboard.io.fu_done(FU_LSU) := ex_is_lsu && lsu_completing

  // WB Stage
  regfile.write_preg := ex_wb("rd")
  regfile.write_data := ex_wb("wb_data")
  regfile.write_en   := ex_wb("regwrite").asBool

  val instret = ex_wb("instr") =/= p(Bubble).value.U(p(ILen).W)

  cycle_count   := cycle_count + 1.U
  instret_count := instret_count + Mux(instret, 1.U, 0.U)

  csrfile.foreach { csr =>
    csr.extraInputIO("cycle")   := cycle_count
    csr.extraInputIO("instret") := instret_count

    if (csr.extraInputIO.contains("timer_irq")) csr.extraInputIO("timer_irq") := irq.timer_irq
    if (csr.extraInputIO.contains("soft_irq")) csr.extraInputIO("soft_irq")   := irq.soft_irq
    if (csr.extraInputIO.contains("ext_irq")) csr.extraInputIO("ext_irq")     := irq.ext_irq
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

  val debug_l1_icache_access = IO(Output(Bool()))
  val debug_l1_icache_miss   = IO(Output(Bool()))
  val debug_l1_dcache_access = IO(Output(Bool()))
  val debug_l1_dcache_miss   = IO(Output(Bool()))

  debug_cycle_count   := cycle_count
  debug_instret_count := instret_count
  debug_instret       := instret
  debug_pc            := ex_wb("pc")
  debug_instr         := ex_wb("instr")
  debug_reg_addr      := ex_wb("rd")
  debug_reg_we        := ex_wb("regwrite").asBool
  debug_reg_data      := ex_wb("wb_data")

  debug_branch_taken  := bru.taken
  debug_branch_source := bru.pc
  debug_branch_target := bru.target

  debug_l1_icache_access := RegNext(l1_icache.upper.req.fire)
  debug_l1_icache_miss   := !l1_icache.upper.resp.bits.hit
  debug_l1_dcache_access := RegNext(l1_dcache.upper.req.fire)
  debug_l1_dcache_miss   := !l1_dcache.upper.resp.bits.hit
}
