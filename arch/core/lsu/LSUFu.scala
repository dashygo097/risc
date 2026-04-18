package arch.core.lsu

import arch.core.ooo._
import arch.core.imm._
import arch.core.pma._
import arch.configs._
import vopts.mem.cache.CacheIO
import chisel3._
import chisel3.util._

class LsuFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_lsu_fu"

  val mem  = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))
  val mmio = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))

  val lsu       = Module(new Lsu)
  val imm_utils = ImmUtilitiesFactory.getOrThrow(p(ISA).name)

  val uop_reg                                            = Reg(new MicroOp)
  val state_idle :: state_wait_resp :: state_done :: Nil = Enum(3)
  val state                                              = RegInit(state_idle)

  io.req.ready := (state === state_idle) || (state === state_done && io.resp.fire)

  when(io.req.fire) {
    state   := state_wait_resp
    uop_reg := io.req.bits
  }.elsewhen(io.flush) {
    state := state_idle
  }.otherwise {
    when(state === state_wait_resp) {
      val resp_fired = lsu.mem.resp.fire || lsu.mmio.resp.fire
      when(resp_fired) {
        state := state_done
      }
    }.elsewhen(state === state_done && io.resp.fire) {
      state := state_idle
    }
  }

  val imm  = imm_utils.genImm(uop_reg.instr, uop_reg.imm_type)
  val addr = uop_reg.rs1_data + imm

  val (_, pma_readable, pma_writable, pma_cacheable) = PmaChecker(addr)

  lsu.en            := (state === state_wait_resp)
  lsu.uop           := uop_reg.uop
  lsu.addr          := addr
  lsu.wdata         := uop_reg.rs2_data
  lsu.pma_readable  := pma_readable
  lsu.pma_writable  := pma_writable
  lsu.pma_cacheable := pma_cacheable

  mem <> lsu.mem
  mmio <> lsu.mmio

  io.resp.valid        := (state === state_done)
  io.resp.bits.result  := lsu.rdata
  io.resp.bits.rd      := uop_reg.rd
  io.resp.bits.pc      := uop_reg.pc
  io.resp.bits.instr   := uop_reg.instr
  io.resp.bits.rob_tag := uop_reg.rob_tag
}
