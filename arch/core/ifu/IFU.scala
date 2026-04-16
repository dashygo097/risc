package arch.core.ifu

import arch.configs._
import chisel3._
import chisel3.util._
import vopts.mem.cache.CacheReadOnlyIO

class Ifu(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_ifu"

  val mem = IO(new CacheReadOnlyIO(Vec(p(IssueWidth), UInt(p(ILen).W)), p(XLen)))

  val bru_taken  = IO(Input(Bool()))
  val bru_target = IO(Input(UInt(p(XLen).W)))

  val bpu_taken_in  = IO(Input(Bool()))
  val bpu_target_in = IO(Input(UInt(p(XLen).W)))

  val bru_not_taken = IO(Input(Bool()))
  val bru_branch_pc = IO(Input(UInt(p(XLen).W)))

  val take_trap   = IO(Input(Bool()))
  val trap_target = IO(Input(UInt(p(XLen).W)))

  val fetch_pc = IO(Output(UInt(p(XLen).W)))

  val fronend_flush = IO(Output(Bool()))

  val if_valid           = IO(Output(Vec(p(IssueWidth), Bool())))
  val if_instr           = IO(Output(Vec(p(IssueWidth), UInt(p(ILen).W))))
  val if_pc              = IO(Output(Vec(p(IssueWidth), UInt(p(XLen).W))))
  val if_bpu_pred_taken  = IO(Output(Vec(p(IssueWidth), Bool())))
  val if_bpu_pred_target = IO(Output(Vec(p(IssueWidth), UInt(p(XLen).W))))

  val dispatch_fire = IO(Input(Vec(p(IssueWidth), Bool())))

  val reset_ibuffer = IO(Output(Bool()))

  val ibuffer = Module(new IBuffer)

  val pc = RegInit(p(ResetVector).U(p(XLen).W))

  val do_redirect = take_trap || bru_taken || bru_not_taken

  class FetchMeta extends Bundle {
    val pc              = UInt(p(XLen).W)
    val bpu_pred_taken  = Bool()
    val bpu_pred_target = UInt(p(XLen).W)
  }

  val meta_q = Module(new Queue(new FetchMeta, 8, hasFlush = true))

  val drop_count   = RegInit(0.U(5.W))
  val pending_reqs = RegInit(0.U(5.W))

  val req_fire  = mem.req.valid && mem.req.ready
  val resp_fire = mem.resp.valid && mem.resp.ready

  val next_drop_count = drop_count + pending_reqs
  val is_dropping     = resp_fire && (next_drop_count > 0.U)

  when(do_redirect) {
    drop_count   := next_drop_count - Mux(is_dropping, 1.U, 0.U)
    pending_reqs := 0.U
  }.otherwise {
    val is_valid_resp_fire = resp_fire && (drop_count === 0.U)
    val is_drop_resp_fire  = resp_fire && (drop_count > 0.U)

    when(req_fire && !is_valid_resp_fire) {
      pending_reqs := pending_reqs + 1.U
    }.elsewhen(!req_fire && is_valid_resp_fire) {
      pending_reqs := pending_reqs - 1.U
    }

    when(is_drop_resp_fire) {
      drop_count := drop_count - 1.U
    }
  }

  val is_valid_resp = (drop_count === 0.U) && !do_redirect
  meta_q.io.flush.get := do_redirect

  val align_bytes   = p(IssueWidth) * (p(ILen) / 8)
  val align_mask    = ~(align_bytes - 1).U(p(XLen).W)
  val aligned_pc    = pc & align_mask
  val next_block_pc = aligned_pc + align_bytes.U

  mem.req.valid     := meta_q.io.enq.ready && ibuffer.io.enq_ready && !do_redirect
  mem.req.bits.addr := aligned_pc
  mem.resp.ready    := ibuffer.io.enq_ready

  fetch_pc := pc

  meta_q.io.enq.valid                := req_fire
  meta_q.io.enq.bits.pc              := pc
  meta_q.io.enq.bits.bpu_pred_taken  := bpu_taken_in
  meta_q.io.enq.bits.bpu_pred_target := bpu_target_in

  when(take_trap) {
    pc := trap_target
  }.elsewhen(bru_taken) {
    pc := bru_target
  }.elsewhen(bru_not_taken) {
    pc := bru_branch_pc + 4.U
  }.elsewhen(req_fire) {
    pc := Mux(bpu_taken_in, bpu_target_in, next_block_pc)
  }

  meta_q.io.deq.ready := resp_fire && is_valid_resp

  val resp_pc  = meta_q.io.deq.bits.pc
  val resp_idx = if (p(IssueWidth) > 1) resp_pc(log2Ceil(align_bytes) - 1, log2Ceil(p(ILen) / 8)) else 0.U

  for (w <- 0 until p(IssueWidth)) {
    val is_valid_pos = w.U >= resp_idx
    ibuffer.io.enq_valid(w)                := resp_fire && is_valid_resp && meta_q.io.deq.valid && is_valid_pos
    ibuffer.io.enq_bits(w).pc              := (resp_pc & align_mask) + (w * (p(ILen) / 8)).U
    ibuffer.io.enq_bits(w).instr           := mem.resp.bits.data(w)
    ibuffer.io.enq_bits(w).bpu_pred_taken  := meta_q.io.deq.bits.bpu_pred_taken
    ibuffer.io.enq_bits(w).bpu_pred_target := meta_q.io.deq.bits.bpu_pred_target
  }

  ibuffer.io.flush := do_redirect

  val flush_cond = do_redirect

  for (w <- 0 until p(IssueWidth)) {
    ibuffer.io.deq(w).ready := dispatch_fire(w)

    if_valid(w)           := ibuffer.io.deq(w).valid && !flush_cond
    if_instr(w)           := ibuffer.io.deq(w).bits.instr
    if_pc(w)              := ibuffer.io.deq(w).bits.pc
    if_bpu_pred_taken(w)  := ibuffer.io.deq(w).bits.bpu_pred_taken
    if_bpu_pred_target(w) := ibuffer.io.deq(w).bits.bpu_pred_target
  }

  fronend_flush := flush_cond
  reset_ibuffer := do_redirect
}
