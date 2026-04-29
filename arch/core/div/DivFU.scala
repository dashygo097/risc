package arch.core.div

import arch.configs._
import arch.core.ooo._
import chisel3._
import chisel3.util.{ switch, is }

object DivFuState extends ChiselEnum {
  val IDLE, BUSY, DONE = Value
}

class DivFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_div_fu"

  val div       = Module(new Div)
  val div_utils = DivUtilsFactory.getOrThrow(p(ISA).name)

  val uop_reg    = Reg(new MicroOp)
  val result_reg = RegInit(0.U(p(XLen).W))
  val state      = RegInit(DivFuState.IDLE)

  io.req.ready := state === DivFuState.IDLE

  val start      = io.req.fire
  val active_uop = Mux(start, io.req.bits.uop, uop_reg.uop)
  val ctrl       = div_utils.decode(active_uop)

  div.en        := start
  div.kill      := io.flush
  div.src1      := Mux(start, io.req.bits.rs1_data, uop_reg.rs1_data)
  div.src2      := Mux(start, io.req.bits.rs2_data, uop_reg.rs2_data)
  div.is_signed := ctrl.is_signed
  div.is_rem    := ctrl.is_rem

  when(io.flush) {
    state := DivFuState.IDLE
  }.otherwise {
    switch(state) {
      is(DivFuState.IDLE) {
        when(start) {
          uop_reg := io.req.bits
          state   := DivFuState.BUSY
        }
      }

      is(DivFuState.BUSY) {
        when(div.done) {
          result_reg := div.result
          state      := DivFuState.DONE
        }
      }

      is(DivFuState.DONE) {
        when(io.resp.fire) {
          state := DivFuState.IDLE
        }
      }
    }
  }

  io.resp.valid        := state === DivFuState.DONE
  io.resp.bits.result  := result_reg
  io.resp.bits.rd      := uop_reg.rd
  io.resp.bits.pc      := uop_reg.pc
  io.resp.bits.instr   := uop_reg.instr
  io.resp.bits.rob_tag := uop_reg.rob_tag
}
