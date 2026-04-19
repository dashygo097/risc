package arch.core.mult

import arch.configs._
import arch.core.ooo._
import chisel3._

object MultFuState extends ChiselEnum {
  val IDLE, BUSY, DONE = Value
}

class MultFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_mult_fu"

  val mult       = Module(new Mult)
  val mult_utils = MultUtilsFactory.getOrThrow(p(ISA).name)

  val req_reg = Reg(new MicroOp)
  val state   = RegInit(MultFuState.IDLE)

  io.req.ready := (state === MultFuState.IDLE) || (state === MultFuState.DONE && io.resp.fire)

  when(io.req.fire) {
    state   := MultFuState.BUSY
    req_reg := io.req.bits
  }.elsewhen(io.flush) {
    state := MultFuState.IDLE
  }.otherwise {
    when(state === MultFuState.BUSY && mult.done) {
      state := MultFuState.DONE
    }.elsewhen(state === MultFuState.DONE && io.resp.fire) {
      state := MultFuState.IDLE
    }
  }

  val current_uop = Mux(io.req.fire, io.req.bits.uop, req_reg.uop)
  val ctrl        = mult_utils.decode(current_uop)

  mult.en   := io.req.fire
  mult.kill := io.flush
  mult.src1 := Mux(io.req.fire, io.req.bits.rs1_data, req_reg.rs1_data)
  mult.src2 := Mux(io.req.fire, io.req.bits.rs2_data, req_reg.rs2_data)

  mult.a_signed := ctrl.a_signed
  mult.b_signed := ctrl.b_signed
  mult.high     := ctrl.high

  io.resp.valid        := (state === MultFuState.DONE)
  io.resp.bits.result  := mult.result
  io.resp.bits.rd      := req_reg.rd
  io.resp.bits.pc      := req_reg.pc
  io.resp.bits.instr   := req_reg.instr
  io.resp.bits.rob_tag := req_reg.rob_tag
}
