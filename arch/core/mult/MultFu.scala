package arch.core.mult

import arch.configs._
import arch.core.decoder._
import arch.core.ooo._
import chisel3._
import chisel3.util._

class MultFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_mult_fu"

  val core_mult = Module(new Mult)
  val decoder   = Module(new Decoder)

  val req_reg                                       = Reg(new MicroOp)
  val state_idle :: state_busy :: state_done :: Nil = Enum(3)
  val state                                         = RegInit(state_idle)

  io.req.ready := (state === state_idle) || (state === state_done && io.resp.fire)

  when(io.req.fire) {
    state   := state_busy
    req_reg := io.req.bits
  }.elsewhen(io.flush) {
    state := state_idle
  }.otherwise {
    when(state === state_busy && core_mult.io.done) {
      state := state_done
    }.elsewhen(state === state_done && io.resp.fire) {
      state := state_idle
    }
  }

  val current_instr = Mux(io.req.fire, io.req.bits.instr, req_reg.instr)
  decoder.instr := current_instr

  core_mult.io.en       := io.req.fire
  core_mult.io.kill     := io.flush
  core_mult.io.src1     := Mux(io.req.fire, io.req.bits.rs1_data, req_reg.rs1_data)
  core_mult.io.src2     := Mux(io.req.fire, io.req.bits.rs2_data, req_reg.rs2_data)
  core_mult.io.a_signed := decoder.decoded.mult_a_signed
  core_mult.io.b_signed := decoder.decoded.mult_b_signed
  core_mult.io.high     := decoder.decoded.mult_high

  io.resp.valid        := (state === state_done)
  io.resp.bits.result  := core_mult.io.result
  io.resp.bits.rd      := req_reg.rd
  io.resp.bits.pc      := req_reg.pc
  io.resp.bits.instr   := req_reg.instr
  io.resp.bits.rob_tag := req_reg.rob_tag
}
