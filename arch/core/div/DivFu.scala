package arch.core.div

import arch.configs._
import arch.core.decoder._
import arch.core.ooo._
import chisel3._
import chisel3.util._

class DivFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_div_fu"

  val core_div = Module(new Div)
  val decoder = Module(new Decoder)

  val req_reg = Reg(new MicroOp)
  val states = Enum(3)
  val state_idle = states(0)
  val state_busy = states(1)
  val state_done = states(2)
  val state = RegInit(state_idle)

  io.req.ready := (state === state_idle) || (state === state_done && io.resp.fire)

  when(io.req.fire) {
    state := state_busy
    req_reg := io.req.bits
  }.elsewhen(io.flush) {
    state := state_idle
  }.otherwise {
    when(state === state_busy && core_div.done) {
      state := state_done
    }.elsewhen(state === state_done && io.resp.fire) {
      state := state_idle
    }
  }

  val current_instr = Mux(io.req.fire, io.req.bits.instr, req_reg.instr)
  decoder.instr := current_instr

  core_div.en := io.req.fire
  core_div.kill := io.flush
  core_div.src1 := Mux(io.req.fire, io.req.bits.rs1_data, req_reg.rs1_data)
  core_div.src2 := Mux(io.req.fire, io.req.bits.rs2_data, req_reg.rs2_data)
  core_div.is_signed := decoder.decoded.div_signed
  core_div.is_rem := decoder.decoded.div_rem

  io.resp.valid := (state === state_done)
  io.resp.bits.result := core_div.result
  io.resp.bits.rd := req_reg.rd
  io.resp.bits.pc := req_reg.pc
  io.resp.bits.instr := req_reg.instr
  io.resp.bits.rob_tag := req_reg.rob_tag
}
