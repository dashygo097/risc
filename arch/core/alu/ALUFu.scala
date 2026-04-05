package arch.core.alu

import arch.configs._
import arch.core.imm._
import arch.core.decoder._
import arch.core.ooo._
import chisel3._
import chisel3.util.MuxLookup

class AluFU(implicit p: Parameters) extends FunctionalUnit with AluConsts {
  override def desiredName: String = s"${p(ISA).name}_alu_fu"

  val core_alu = Module(new Alu)
  val decoder  = Module(new Decoder)

  val imm_utils = ImmUtilitiesFactory.getOrThrow(p(ISA).name)

  val req_reg = Reg(new MicroOp)
  val valid   = RegInit(false.B)

  io.req.ready := !valid || io.resp.fire

  when(io.req.fire) {
    valid   := true.B
    req_reg := io.req.bits
  }.elsewhen(io.resp.fire || io.flush) {
    valid := false.B
  }

  decoder.instr := req_reg.instr

  val src1 = MuxLookup(decoder.decoded.alu_sel1, 0.U(p(XLen).W))(
    Seq(
      A1_ZERO.value.U(SZ_A1.W) -> 0.U(p(XLen).W),
      A1_RS1.value.U(SZ_A1.W)  -> req_reg.rs1_data,
      A1_PC.value.U(SZ_A1.W)   -> req_reg.pc
    )
  )

  val src2 = MuxLookup(decoder.decoded.alu_sel2, 0.U(p(XLen).W))(
    Seq(
      A2_ZERO.value.U(SZ_A2.W)   -> 0.U(p(XLen).W),
      A2_RS2.value.U(SZ_A2.W)    -> req_reg.rs2_data,
      A2_IMM.value.U(SZ_A2.W)    -> imm_utils.genImm(req_reg.instr, decoder.decoded.imm_type),
      A2_PCSTEP.value.U(SZ_A2.W) -> p(IAlign).U(p(XLen).W)
    )
  )

  core_alu.en     := valid
  core_alu.src1   := src1
  core_alu.src2   := src2
  core_alu.fnType := decoder.decoded.alu_fn
  core_alu.mode   := decoder.decoded.alu_mode

  io.resp.valid        := valid
  io.resp.bits.result  := core_alu.result
  io.resp.bits.rd      := req_reg.rd
  io.resp.bits.pc      := req_reg.pc
  io.resp.bits.instr   := req_reg.instr
  io.resp.bits.rob_tag := req_reg.rob_tag
}
