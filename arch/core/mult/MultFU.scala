package arch.core.mult

import arch.configs._
import arch.core.ooo._
import chisel3._
import chisel3.util.{ switch, is }

object MultFuState extends ChiselEnum {
  val IDLE, BUSY, DONE = Value
}

class MultFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_mult_fu"

  val mult       = Module(new Mult)
  val mult_utils = MultUtilsFactory.getOrThrow(p(ISA).name)

  val uop_reg    = Reg(new MicroOp)
  val result_reg = RegInit(0.U(p(XLen).W))
  val state      = RegInit(MultFuState.IDLE)

  io.req.ready := state === MultFuState.IDLE

  val start      = io.req.fire
  val active_uop = Mux(start, io.req.bits.uop, uop_reg.uop)
  val ctrl       = mult_utils.decode(active_uop)

  mult.en       := start
  mult.kill     := io.flush
  mult.src1     := Mux(start, io.req.bits.rs1_data, uop_reg.rs1_data)
  mult.src2     := Mux(start, io.req.bits.rs2_data, uop_reg.rs2_data)
  mult.a_signed := ctrl.a_signed
  mult.b_signed := ctrl.b_signed
  mult.high     := ctrl.high

  when(io.flush) {
    state := MultFuState.IDLE
  }.otherwise {
    switch(state) {
      is(MultFuState.IDLE) {
        when(start) {
          uop_reg := io.req.bits
          state   := MultFuState.BUSY
        }
      }

      is(MultFuState.BUSY) {
        when(mult.done) {
          result_reg := mult.result
          state      := MultFuState.DONE
        }
      }

      is(MultFuState.DONE) {
        when(io.resp.fire) {
          state := MultFuState.IDLE
        }
      }
    }
  }

  io.resp.valid        := state === MultFuState.DONE
  io.resp.bits.result  := result_reg
  io.resp.bits.rd      := uop_reg.rd
  io.resp.bits.pc      := uop_reg.pc
  io.resp.bits.instr   := uop_reg.instr
  io.resp.bits.rob_tag := uop_reg.rob_tag
}
