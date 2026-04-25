package arch.core.lsu

import arch.core.ooo._
import arch.core.imm._
import arch.core.pma._
import arch.configs._
import vopts.mem.cache.CacheIO
import chisel3._
import chisel3.util.{ switch, is }

object LsuFuState extends ChiselEnum {
  val IDLE, WAIT_RESP, DONE, FLUSH_DRAIN = Value
}

class LsuFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_lsu_fu"

  val mem  = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))
  val mmio = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))

  val lsu       = Module(new Lsu)
  val imm_utils = ImmUtilsFactory.getOrThrow(p(ISA).name)

  val uop_reg = Reg(new MicroOp)
  val state   = RegInit(LsuFuState.IDLE)

  val req_sent = RegInit(false.B)

  val mem_req_fired = lsu.mem.req.fire || lsu.mmio.req.fire
  val resp_fired    = lsu.mem.resp.fire || lsu.mmio.resp.fire

  when(state === LsuFuState.IDLE) {
    req_sent := false.B
  }.elsewhen(mem_req_fired) {
    req_sent := true.B
  }.elsewhen(resp_fired) {
    req_sent := false.B
  }

  io.req.ready := (state === LsuFuState.IDLE) || (state === LsuFuState.DONE && io.resp.fire && !io.flush)

  when(io.flush) {
    val needs_drain = ((state === LsuFuState.WAIT_RESP) && (req_sent || mem_req_fired)) || (state === LsuFuState.FLUSH_DRAIN)

    when(needs_drain && !resp_fired) {
      state := LsuFuState.FLUSH_DRAIN
    }.otherwise {
      state := LsuFuState.IDLE
    }
  }.otherwise {
    switch(state) {
      is(LsuFuState.IDLE) {
        when(io.req.fire) {
          state   := LsuFuState.WAIT_RESP
          uop_reg := io.req.bits
        }
      }
      is(LsuFuState.WAIT_RESP) {
        when(resp_fired) {
          state := LsuFuState.DONE
        }
      }
      is(LsuFuState.FLUSH_DRAIN) {
        when(resp_fired) {
          state := LsuFuState.IDLE
        }
      }
      is(LsuFuState.DONE) {
        when(io.resp.fire) {
          when(io.req.fire) {
            state   := LsuFuState.WAIT_RESP
            uop_reg := io.req.bits
          }.otherwise {
            state := LsuFuState.IDLE
          }
        }
      }
    }
  }

  val imm  = imm_utils.genImm(uop_reg.instr, uop_reg.imm_type)
  val addr = uop_reg.rs1_data + imm

  val (_, pma_readable, pma_writable, pma_cacheable) = PmaChecker(addr)

  lsu.en            := (state === LsuFuState.WAIT_RESP)
  lsu.uop           := uop_reg.uop
  lsu.addr          := addr
  lsu.wdata         := uop_reg.rs2_data
  lsu.pma_readable  := pma_readable
  lsu.pma_writable  := pma_writable
  lsu.pma_cacheable := pma_cacheable

  mem <> lsu.mem
  mmio <> lsu.mmio

  io.resp.valid        := (state === LsuFuState.DONE)
  io.resp.bits.result  := lsu.rdata
  io.resp.bits.rd      := uop_reg.rd
  io.resp.bits.pc      := uop_reg.pc
  io.resp.bits.instr   := uop_reg.instr
  io.resp.bits.rob_tag := uop_reg.rob_tag
}
