package arch.core.mult

import arch.configs._
import arch.core.ooo._
import chisel3._
import chisel3.util._

class MultFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_mult_fu"

  val mult       = Module(new Mult)
  val mult_utils = MultUtilitiesFactory.getOrThrow(p(ISA).name)

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
    when(state === state_busy && mult.done) {
      state := state_done
    }.elsewhen(state === state_done && io.resp.fire) {
      state := state_idle
    }
  }

  val current_uop = Mux(io.req.fire, io.req.bits.uop, req_reg.uop)
  val ctrl        = mult_utils.decodeUop(current_uop)

  mult.en   := io.req.fire
  mult.kill := io.flush
  mult.src1 := Mux(io.req.fire, io.req.bits.rs1_data, req_reg.rs1_data)
  mult.src2 := Mux(io.req.fire, io.req.bits.rs2_data, req_reg.rs2_data)

  mult.a_signed := ctrl.a_signed
  mult.b_signed := ctrl.b_signed
  mult.high     := ctrl.high

  io.resp.valid        := (state === state_done)
  io.resp.bits.result  := mult.result
  io.resp.bits.rd      := req_reg.rd
  io.resp.bits.pc      := req_reg.pc
  io.resp.bits.instr   := req_reg.instr
  io.resp.bits.rob_tag := req_reg.rob_tag
}
