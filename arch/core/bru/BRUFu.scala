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
  val imm_utils = ImmUtilitiesFactory.getOrThrow(p(ISA).name)

  val busy    = RegInit(false.B)
  val uop_reg = Reg(new MicroOp)

  io.req.ready := !busy || io.resp.ready

  when(io.flush) {
    busy := false.B
  }.elsewhen(io.req.fire) {
    busy    := true.B
    uop_reg := io.req.bits
  }.elsewhen(io.resp.fire) {
    busy := false.B
  }

  val active_uop = Mux(busy, uop_reg.uop, 0.U)

  bru.en   := busy
  bru.pc   := uop_reg.pc
  bru.src1 := uop_reg.rs1_data
  bru.src2 := uop_reg.rs2_data
  bru.uop  := active_uop
  bru.imm  := imm_utils.genImm(uop_reg.instr, uop_reg.imm_type)

  io.resp.valid        := busy && !io.flush
  io.resp.bits.pc      := uop_reg.pc
  io.resp.bits.instr   := uop_reg.instr
  io.resp.bits.rd      := uop_reg.rd
  io.resp.bits.rob_tag := uop_reg.rob_tag

  io.resp.bits.result := uop_reg.pc + p(IAlign).U

  actual_taken  := bru.taken
  actual_target := Mux(bru.taken, bru.target, uop_reg.pc + p(IAlign).U)
}
