package arch.core.ifu

import arch.configs._
import vopts.mem.cache.CacheReadOnlyIO
import chisel3._

class Ifu(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_ifu"

  val mem = IO(new CacheReadOnlyIO(UInt(p(XLen).W), p(XLen)))

  val bru_taken       = IO(Input(Bool()))
  val bru_target      = IO(Input(UInt(p(XLen).W)))
  val stall           = IO(Input(Bool()))
  val load_use_hazard = IO(Input(Bool()))
  val lsu_busy        = IO(Input(Bool()))

  val bpu_taken_in  = IO(Input(Bool()))
  val bpu_target_in = IO(Input(UInt(p(XLen).W)))

  val bru_not_taken = IO(Input(Bool()))
  val bru_branch_pc = IO(Input(UInt(p(XLen).W)))

  val take_trap   = IO(Input(Bool()))
  val trap_target = IO(Input(UInt(p(XLen).W)))

  val fetch_pc = IO(Output(UInt(p(XLen).W)))

  val fronend_stall = IO(Output(Bool()))
  val fronend_flush = IO(Output(Bool()))

  val if_instr           = IO(Output(Vec(p(IssueWidth), UInt(p(ILen).W))))
  val if_pc              = IO(Output(Vec(p(IssueWidth), UInt(p(XLen).W))))
  val if_bpu_pred_taken  = IO(Output(Vec(p(IssueWidth), Bool())))
  val if_bpu_pred_target = IO(Output(Vec(p(IssueWidth), UInt(p(XLen).W))))

  val reset_ibuffer = IO(Output(Bool()))

  val ibuffer = Module(new IBuffer)

  val pc = RegInit(p(ResetVector).U(p(XLen).W))

  val reset_ibuffer_reg = RegInit(false.B)
  val imem_pending      = RegInit(false.B)
  val imem_data         = RegInit(p(Bubble).value.U(p(ILen).W))

  val imem_pc    = RegInit(p(ResetVector).U(p(XLen).W))
  val imem_valid = RegInit(false.B)

  val bpu_pred_taken  = RegInit(false.B)
  val bpu_pred_target = RegInit(0.U(p(XLen).W))

  mem.req.valid     := !imem_pending && !ibuffer.full
  mem.req.bits.addr := pc
  mem.resp.ready    := true.B

  fetch_pc := pc

  val icache_req_fire  = mem.req.valid && mem.req.ready
  val icache_resp_fire = mem.resp.valid && mem.resp.ready

  when(icache_req_fire) {
    imem_pending    := true.B
    imem_pc         := pc
    imem_valid      := true.B
    bpu_pred_taken  := bpu_taken_in
    bpu_pred_target := bpu_target_in
  }

  val do_redirect = take_trap || bru_taken || bru_not_taken

  when(do_redirect) {
    imem_valid     := false.B
    bpu_pred_taken := false.B
  }

  when(icache_resp_fire) {
    imem_data    := mem.resp.bits.data
    imem_pending := false.B
  }

  when(reset_ibuffer_reg) {
    imem_valid := false.B
  }

  when(do_redirect) {
    reset_ibuffer_reg := true.B
  }

  when(ibuffer.empty && !imem_pending) {
    reset_ibuffer_reg := false.B
  }

  ibuffer.flush := do_redirect

  ibuffer.enq.valid                := icache_resp_fire && imem_valid && !ibuffer.full
  ibuffer.enq.bits.pc              := imem_pc
  ibuffer.enq.bits.instr           := mem.resp.bits.data
  ibuffer.enq.bits.bpu_pred_taken  := bpu_pred_taken
  ibuffer.enq.bits.bpu_pred_target := bpu_pred_target

  val stall_cond = stall || load_use_hazard
  val flush_cond = (do_redirect || !imem_valid || reset_ibuffer_reg) && !lsu_busy

  for (w <- 0 until p(IssueWidth)) {
    ibuffer.deq(w).ready  := !stall_cond && !flush_cond
    if_instr(w)           := Mux(ibuffer.deq(w).fire, ibuffer.deq(w).bits.instr, p(Bubble).value.U(p(ILen).W))
    if_pc(w)              := Mux(ibuffer.deq(w).fire, ibuffer.deq(w).bits.pc, 0.U(p(XLen).W))
    if_bpu_pred_taken(w)  := Mux(ibuffer.deq(w).fire, ibuffer.deq(w).bits.bpu_pred_taken, false.B)
    if_bpu_pred_target(w) := Mux(ibuffer.deq(w).fire, ibuffer.deq(w).bits.bpu_pred_target, 0.U(p(XLen).W))
  }

  fronend_stall := stall_cond
  fronend_flush := flush_cond
  reset_ibuffer := reset_ibuffer_reg

  when(take_trap && !lsu_busy) {
    pc := trap_target
  }.elsewhen(bru_taken && !lsu_busy) {
    pc := bru_target
  }.elsewhen(bru_not_taken && !lsu_busy) {
    pc := bru_branch_pc + 4.U
  }.elsewhen(ibuffer.enq.fire) {
    pc := Mux(bpu_pred_taken, bpu_pred_target, pc + 4.U(p(XLen).W))
  }
}
