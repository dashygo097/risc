package arch.core.bru

import arch.core.imm._
import arch.core.ooo._
import arch.configs._
import chisel3._

class BruFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_bru_fu"

  val actual_taken  = IO(Output(Bool()))
  val actual_target = IO(Output(UInt(p(XLen).W)))

  val bru       = Module(new Bru)
  val imm_utils = ImmUtilsFactory.getOrThrow(p(ISA).name)

  val busy    = RegInit(false.B)
  val uop_reg = Reg(new MicroOp)

  io.req.ready := !busy

  when(io.flush) {
    busy := false.B
  }.elsewhen(io.req.fire) {
    busy    := true.B
    uop_reg := io.req.bits
  }.elsewhen(io.resp.fire) {
    busy := false.B
  }

  bru.en   := busy && !io.flush
  bru.pc   := uop_reg.pc
  bru.src1 := uop_reg.rs1_data
  bru.src2 := uop_reg.rs2_data
  bru.uop  := uop_reg.uop
  bru.imm  := imm_utils.genImm(uop_reg.instr, uop_reg.imm_type)

  val resolved_taken = bru.jump || bru.taken
  val fallthrough    = uop_reg.pc + p(PCStep).U

  io.resp.valid        := busy && !io.flush
  io.resp.bits.result  := fallthrough
  io.resp.bits.rd      := uop_reg.rd
  io.resp.bits.pc      := uop_reg.pc
  io.resp.bits.instr   := uop_reg.instr
  io.resp.bits.rob_tag := uop_reg.rob_tag

  actual_taken  := resolved_taken
  actual_target := Mux(resolved_taken, bru.target, fallthrough)
}
