package arch.core.lsu

import arch.core.ooo._
import arch.core.imm._
import arch.core.pma._
import arch.configs._
import vopts.mem.cache.CacheIO
import chisel3._

object LsuFuState extends ChiselEnum {
  val IDLE, WAIT_RESP, DONE = Value
}

class LsuFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_lsu_fu"

  val mem  = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))
  val mmio = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))

  val core_lsu  = Module(new Lsu)
  val imm_utils = ImmUtilsFactory.getOrThrow(p(ISA).name)

  val req_reg = Reg(new MicroOp)
  val state   = RegInit(LsuFuState.IDLE)

  io.req.ready := (state === LsuFuState.IDLE) || (state === LsuFuState.DONE && io.resp.fire)

  when(io.req.fire) {
    state   := LsuFuState.WAIT_RESP
    req_reg := io.req.bits
  }.elsewhen(io.flush) {
    state := LsuFuState.IDLE
  }.otherwise {
    when(state === LsuFuState.WAIT_RESP) {
      val resp_fired = core_lsu.mem.resp.fire || core_lsu.mmio.resp.fire
      when(resp_fired) {
        state := LsuFuState.DONE
      }
    }.elsewhen(state === LsuFuState.DONE && io.resp.fire) {
      state := LsuFuState.IDLE
    }
  }

  val imm  = imm_utils.genImm(req_reg.instr, req_reg.imm_type)
  val addr = req_reg.rs1_data + imm

  val (_, pma_readable, pma_writable, pma_cacheable) = PmaChecker(addr)

  core_lsu.en            := (state === LsuFuState.WAIT_RESP)
  core_lsu.uop           := req_reg.uop
  core_lsu.addr          := addr
  core_lsu.wdata         := req_reg.rs2_data
  core_lsu.pma_readable  := pma_readable
  core_lsu.pma_writable  := pma_writable
  core_lsu.pma_cacheable := pma_cacheable

  mem <> core_lsu.mem
  mmio <> core_lsu.mmio

  io.resp.valid        := (state === LsuFuState.DONE)
  io.resp.bits.result  := core_lsu.rdata
  io.resp.bits.rd      := req_reg.rd
  io.resp.bits.pc      := req_reg.pc
  io.resp.bits.instr   := req_reg.instr
  io.resp.bits.rob_tag := req_reg.rob_tag
}
