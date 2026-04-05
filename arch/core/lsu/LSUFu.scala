package arch.core.lsu

import arch.core.ooo._
import arch.configs._
import arch.core.decoder._
import arch.core.imm._
import arch.core.pma._
import vopts.mem.cache.CacheIO
import chisel3._
import chisel3.util._

class LsuFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_lsu_fu"

  val mem  = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))
  val mmio = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))

  val core_lsu  = Module(new Lsu)
  val decoder   = Module(new Decoder)
  val imm_utils = ImmUtilitiesFactory.getOrThrow(p(ISA).name)

  val req_reg                                            = Reg(new MicroOp)
  val state_idle :: state_wait_resp :: state_done :: Nil = Enum(3)
  val state                                              = RegInit(state_idle)

  io.req.ready := (state === state_idle) || (state === state_done && io.resp.fire)

  when(io.req.fire) {
    state   := state_wait_resp
    req_reg := io.req.bits
  }.elsewhen(io.flush) {
    state := state_idle
  }.otherwise {
    when(state === state_wait_resp) {
      val resp_fired = core_lsu.mem.resp.fire || core_lsu.mmio.resp.fire
      when(resp_fired) {
        state := state_done
      }
    }.elsewhen(state === state_done && io.resp.fire) {
      state := state_idle
    }
  }

  core_lsu.en := (state === state_wait_resp)

  decoder.instr := req_reg.instr

  val imm                                            = imm_utils.genImm(req_reg.instr, decoder.decoded.imm_type)
  val addr                                           = req_reg.rs1_data + imm
  val (_, pma_readable, pma_writable, pma_cacheable) = PmaChecker(addr)

  core_lsu.cmd           := decoder.decoded.lsu_cmd
  core_lsu.addr          := addr
  core_lsu.wdata         := req_reg.rs2_data
  core_lsu.pma_readable  := pma_readable
  core_lsu.pma_writable  := pma_writable
  core_lsu.pma_cacheable := pma_cacheable

  mem <> core_lsu.mem
  mmio <> core_lsu.mmio

  io.resp.valid        := (state === state_done)
  io.resp.bits.result  := core_lsu.rdata
  io.resp.bits.rd      := req_reg.rd
  io.resp.bits.pc      := req_reg.pc
  io.resp.bits.instr   := req_reg.instr
  io.resp.bits.rob_tag := req_reg.rob_tag
}
