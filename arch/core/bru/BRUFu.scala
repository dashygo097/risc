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
  val req_reg = Reg(new MicroOp)

  io.req.ready := !busy || io.resp.ready

  when(io.flush) {
    busy := false.B
  }.elsewhen(io.req.fire) {
    busy    := true.B
    req_reg := io.req.bits
  }.elsewhen(io.resp.fire) {
    busy := false.B
  }

  val active_uop = Mux(busy, req_reg.uop, 0.U)

  bru.en   := busy
  bru.pc   := req_reg.pc
  bru.src1 := req_reg.rs1_data
  bru.src2 := req_reg.rs2_data
  bru.uop  := active_uop
  bru.imm  := imm_utils.genImm(req_reg.instr, req_reg.imm_type)

  io.resp.valid        := busy && !io.flush
  io.resp.bits.pc      := req_reg.pc
  io.resp.bits.instr   := req_reg.instr
  io.resp.bits.rd      := req_reg.rd
  io.resp.bits.rob_tag := req_reg.rob_tag

  io.resp.bits.result := req_reg.pc + p(IAlign).U

  actual_taken  := bru.taken
  actual_target := Mux(bru.taken, bru.target, req_reg.pc + p(IAlign).U)
}
