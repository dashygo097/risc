package arch.core.mult

import arch.configs._
import arch.core.ooo._
import chisel3._
import chisel3.util._

class MultFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_mult_fu"

  val mult       = Module(new Mult)
  val mult_utils = MultUtilsFactory.getOrThrow(p(ISA).name)

  val uop_reg                                       = Reg(new MicroOp)
  val state_idle :: state_busy :: state_done :: Nil = Enum(3)
  val state                                         = RegInit(state_idle)

  io.req.ready := (state === state_idle) || (state === state_done && io.resp.fire)

  when(io.req.fire) {
    state   := state_busy
    uop_reg := io.req.bits
  }.elsewhen(io.flush) {
    state := state_idle
  }.otherwise {
    when(state === state_busy && mult.done) {
      state := state_done
    }.elsewhen(state === state_done && io.resp.fire) {
      state := state_idle
    }
  }

  val current_uop = Mux(io.req.fire, io.req.bits.uop, uop_reg.uop)
  val ctrl        = mult_utils.decode(current_uop)

  mult.en   := io.req.fire
  mult.kill := io.flush
  mult.src1 := Mux(io.req.fire, io.req.bits.rs1_data, uop_reg.rs1_data)
  mult.src2 := Mux(io.req.fire, io.req.bits.rs2_data, uop_reg.rs2_data)

  mult.a_signed := ctrl.a_signed
  mult.b_signed := ctrl.b_signed
  mult.high     := ctrl.high

  io.resp.valid        := (state === state_done)
  io.resp.bits.result  := mult.result
  io.resp.bits.rd      := uop_reg.rd
  io.resp.bits.pc      := uop_reg.pc
  io.resp.bits.instr   := uop_reg.instr
  io.resp.bits.rob_tag := uop_reg.rob_tag
}
