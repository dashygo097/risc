package arch.core.div

import arch.configs._
import arch.core.ooo._
import chisel3._

object DivFuState extends ChiselEnum {
  val IDLE, BUSY, DONE = Value
}

class DivFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_div_fu"

  val div       = Module(new Div)
  val div_utils = DivUtilsFactory.getOrThrow(p(ISA).name)

  val uop_reg    = Reg(new MicroOp)
  val result_reg = Reg(UInt(p(XLen).W))
  val state      = RegInit(DivFuState.IDLE)

  io.req.ready := (state === DivFuState.IDLE) || (state === DivFuState.DONE && io.resp.fire)

  when(io.req.fire) {
    state   := DivFuState.BUSY
    uop_reg := io.req.bits
  }.elsewhen(io.flush) {
    state := DivFuState.IDLE
  }.otherwise {
    when(state === DivFuState.BUSY && div.done) {
      result_reg := div.result
      state      := DivFuState.DONE
    }.elsewhen(state === DivFuState.DONE && io.resp.fire) {
      state := DivFuState.IDLE
    }
  }

  val current_uop = Mux(io.req.fire, io.req.bits.uop, uop_reg.uop)
  val ctrl        = div_utils.decode(current_uop)

  div.en        := io.req.fire
  div.kill      := io.flush
  div.src1      := Mux(io.req.fire, io.req.bits.rs1_data, uop_reg.rs1_data)
  div.src2      := Mux(io.req.fire, io.req.bits.rs2_data, uop_reg.rs2_data)
  div.is_signed := ctrl.is_signed
  div.is_rem    := ctrl.is_rem

  io.resp.valid        := (state === DivFuState.DONE)
  io.resp.bits.result  := result_reg
  io.resp.bits.rd      := uop_reg.rd
  io.resp.bits.pc      := uop_reg.pc
  io.resp.bits.instr   := uop_reg.instr
  io.resp.bits.rob_tag := uop_reg.rob_tag
}
