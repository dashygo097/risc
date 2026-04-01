package arch.core.ifu

import arch.configs._
import vopts.mem.cache.CacheReadOnlyIO
import chisel3._
import chisel3.util._

class FetchMeta(implicit p: Parameters) extends Bundle {
  val pc              = UInt(p(XLen).W)
  val bpu_pred_taken  = Bool()
  val bpu_pred_target = UInt(p(XLen).W)
}

class Ifu(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_ifu"

  val mem = IO(new CacheReadOnlyIO(UInt(p(XLen).W), p(XLen)))

  val bru_taken       = IO(Input(Bool()))
  val bru_target      = IO(Input(UInt(p(XLen).W)))
  val id_ex_stall     = IO(Input(Bool()))
  val load_use_hazard = IO(Input(Bool()))
  val lsu_busy        = IO(Input(Bool()))

  val bpu_taken_in  = IO(Input(Bool()))
  val bpu_target_in = IO(Input(UInt(p(XLen).W)))

  val bru_not_taken = IO(Input(Bool()))
  val bru_branch_pc = IO(Input(UInt(p(XLen).W)))

  val take_trap   = IO(Input(Bool()))
  val trap_target = IO(Input(UInt(p(XLen).W)))

  val fetch_pc = IO(Output(UInt(p(XLen).W)))

  val if_id_stall        = IO(Output(Bool()))
  val if_id_flush        = IO(Output(Bool()))
  val if_instr           = IO(Output(UInt(p(ILen).W)))
  val if_pc              = IO(Output(UInt(p(XLen).W)))
  val if_bpu_pred_taken  = IO(Output(Bool()))
  val if_bpu_pred_target = IO(Output(UInt(p(XLen).W)))

  val ibuffer_deq_fire = IO(Output(Bool()))
  val reset_ibuffer    = IO(Output(Bool()))

  val ibuffer = Module(new IBuffer)
  val pc      = RegInit(0.U(p(XLen).W))

  val do_redirect       = take_trap || bru_taken || bru_not_taken
  val reset_ibuffer_reg = RegInit(false.B)

  val inflight_reset = reset.asBool || do_redirect
  val inflight_q     = withReset(inflight_reset) {
    Module(new Queue(new FetchMeta, 4))
  }

  fetch_pc          := pc
  mem.req.valid     := inflight_q.io.enq.ready && !ibuffer.full
  mem.req.bits.addr := pc

  inflight_q.io.enq.valid                := mem.req.fire
  inflight_q.io.enq.bits.pc              := pc
  inflight_q.io.enq.bits.bpu_pred_taken  := bpu_taken_in
  inflight_q.io.enq.bits.bpu_pred_target := bpu_target_in

  when(take_trap && !lsu_busy) {
    pc := trap_target
  }.elsewhen(bru_taken && !lsu_busy) {
    pc := bru_target
  }.elsewhen(bru_not_taken && !lsu_busy) {
    pc := bru_branch_pc + 4.U
  }.elsewhen(mem.req.fire) {
    pc := Mux(bpu_taken_in, bpu_target_in, pc + 4.U(p(XLen).W))
  }

  // Handle Cache Responses
  val valid_resp = mem.resp.valid && inflight_q.io.deq.valid

  ibuffer.enq.valid                := valid_resp && !ibuffer.full
  ibuffer.enq.bits.pc              := inflight_q.io.deq.bits.pc
  ibuffer.enq.bits.instr           := mem.resp.bits.data
  ibuffer.enq.bits.bpu_pred_taken  := inflight_q.io.deq.bits.bpu_pred_taken
  ibuffer.enq.bits.bpu_pred_target := inflight_q.io.deq.bits.bpu_pred_target

  inflight_q.io.deq.ready := mem.resp.fire && inflight_q.io.deq.valid
  mem.resp.ready          := !inflight_q.io.deq.valid || !ibuffer.full

  when(do_redirect)(reset_ibuffer_reg                               := true.B)
  when(ibuffer.empty && !inflight_q.io.deq.valid)(reset_ibuffer_reg := false.B)

  val stall_cond = id_ex_stall || load_use_hazard
  val flush_cond = (do_redirect || reset_ibuffer_reg) && !lsu_busy

  ibuffer.deq.ready := (!ibuffer.empty && !stall_cond && !flush_cond) || reset_ibuffer_reg

  if_id_stall        := stall_cond
  if_id_flush        := flush_cond
  if_instr           := Mux(ibuffer.deq.fire, ibuffer.deq.bits.instr, p(Bubble).value.U(p(ILen).W))
  if_pc              := Mux(ibuffer.deq.fire, ibuffer.deq.bits.pc, 0.U(p(XLen).W))
  if_bpu_pred_taken  := Mux(ibuffer.deq.fire, ibuffer.deq.bits.bpu_pred_taken, false.B)
  if_bpu_pred_target := Mux(ibuffer.deq.fire, ibuffer.deq.bits.bpu_pred_target, 0.U(p(XLen).W))

  ibuffer_deq_fire := ibuffer.deq.fire
  reset_ibuffer    := reset_ibuffer_reg
}
