package arch.core.ifu

import chisel3._
import arch.configs._

class IFU(implicit p: Parameters) extends Module {
  val icache_req_valid = IO(Output(Bool()))
  val icache_req_ready = IO(Input(Bool()))
  val icache_req_addr  = IO(Output(UInt(p(XLen).W)))

  val icache_resp_valid = IO(Input(Bool()))
  val icache_resp_ready = IO(Output(Bool()))
  val icache_resp_data  = IO(Input(UInt(p(XLen).W)))

  val bru_taken       = IO(Input(Bool()))
  val bru_target      = IO(Input(UInt(p(XLen).W)))
  val id_ex_stall     = IO(Input(Bool()))
  val load_use_hazard = IO(Input(Bool()))
  val lsu_busy        = IO(Input(Bool()))

  val if_id_stall = IO(Output(Bool()))
  val if_id_flush = IO(Output(Bool()))
  val if_instr    = IO(Output(UInt(p(ILen).W)))
  val if_pc       = IO(Output(UInt(p(XLen).W)))

  val ibuffer_deq_fire = IO(Output(Bool()))
  val reset_ibuffer    = IO(Output(Bool()))

  val ibuffer = Module(new IBuffer)

  val pc = RegInit(0.U(p(XLen).W))

  val reset_ibuffer_reg = RegInit(false.B)

  val imem_pending = RegInit(false.B)
  val imem_data    = RegInit(p(Bubble).value.U(p(ILen).W))
  val imem_pc      = RegInit(0.U(p(XLen).W))
  val imem_valid   = RegInit(false.B)

  icache_req_valid  := !imem_pending && !ibuffer.full
  icache_req_addr   := pc
  icache_resp_ready := true.B

  val icache_req_fire  = icache_req_valid && icache_req_ready
  val icache_resp_fire = icache_resp_valid && icache_resp_ready

  when(icache_req_fire) {
    imem_pending := true.B
    imem_pc      := pc
    imem_valid   := true.B
  }

  when(bru_taken) {
    imem_valid := false.B
  }

  when(icache_resp_fire) {
    imem_data    := icache_resp_data
    imem_pending := false.B
  }

  when(reset_ibuffer_reg) {
    imem_valid := false.B
  }
  when(bru_taken) {
    reset_ibuffer_reg := true.B
  }
  when(ibuffer.empty && !imem_pending) {
    reset_ibuffer_reg := false.B
  }

  ibuffer.enq.valid      := icache_resp_fire && imem_valid && !ibuffer.full
  ibuffer.enq.bits.pc    := imem_pc
  ibuffer.enq.bits.instr := icache_resp_data

  val stall_cond = id_ex_stall || load_use_hazard
  val flush_cond = (bru_taken || !imem_valid || reset_ibuffer_reg) && !lsu_busy

  ibuffer.deq.ready := (!ibuffer.empty && !stall_cond && !flush_cond) || reset_ibuffer_reg

  if_id_stall := stall_cond
  if_id_flush := flush_cond
  if_instr    := Mux(ibuffer.deq.fire, ibuffer.deq.bits.instr, p(Bubble).value.U(p(ILen).W))
  if_pc       := Mux(ibuffer.deq.fire, ibuffer.deq.bits.pc, p(Bubble).value.U(p(XLen).W))

  ibuffer_deq_fire := ibuffer.deq.fire
  reset_ibuffer    := reset_ibuffer_reg

  when(bru_taken && !lsu_busy) {
    pc := bru_target
  }.elsewhen(ibuffer.enq.fire) {
    pc := pc + 4.U(p(XLen).W)
  }
}
