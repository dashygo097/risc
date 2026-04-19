package arch.core.div

import arch.configs._
import arch.core.ooo._
import chisel3._
import chisel3.util._

class DivFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_div_fu"

  val core_div   = Module(new Div)
  val div_utils  = DivUtilsFactory.getOrThrow(p(ISA).name)

  val req_reg = Reg(new MicroOp)
  val result_reg = Reg(UInt(p(XLen).W))
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
      result_reg := core_div.result
      state := state_done
    }.elsewhen(state === state_done && io.resp.fire) {
      state := state_idle
    }
  }

  val current_uop = Mux(io.req.fire, io.req.bits.uop, req_reg.uop)
  val ctrl        = div_utils.decode(current_uop)

  core_div.en := io.req.fire
  core_div.kill := io.flush
  core_div.src1 := Mux(io.req.fire, io.req.bits.rs1_data, req_reg.rs1_data)
  core_div.src2 := Mux(io.req.fire, io.req.bits.rs2_data, req_reg.rs2_data)
  core_div.is_signed := ctrl.is_signed
  core_div.is_rem := ctrl.is_rem

  io.resp.valid := (state === state_done)
  io.resp.bits.result := result_reg
  io.resp.bits.rd := req_reg.rd
  io.resp.bits.pc := req_reg.pc
  io.resp.bits.instr := req_reg.instr
  io.resp.bits.rob_tag := req_reg.rob_tag
}
