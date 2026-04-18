package arch.core.alu

import arch.configs._
import arch.core.imm._
import arch.core.ooo._
import chisel3._
import chisel3.util.MuxLookup

class AluFU(implicit p: Parameters) extends FunctionalUnit with AluConsts {
  override def desiredName: String = s"${p(ISA).name}_alu_fu"

  val alu       = Module(new Alu)
  val alu_utils = AluUtilitiesFactory.getOrThrow(p(ISA).name)
  val imm_utils = ImmUtilitiesFactory.getOrThrow(p(ISA).name)

  val uop_reg = Reg(new MicroOp)
  val valid   = RegInit(false.B)

  io.req.ready := !valid || io.resp.fire

  when(io.req.fire) {
    valid   := true.B
    uop_reg := io.req.bits
  }.elsewhen(io.resp.fire || io.flush) {
    valid := false.B
  }

  val ctrl = alu_utils.decode(uop_reg.uop)

  val src1 = MuxLookup(ctrl.sel1, 0.U(p(XLen).W))(
    Seq(
      A1_ZERO.value.U(SZ_A1.W) -> 0.U(p(XLen).W),
      A1_RS1.value.U(SZ_A1.W)  -> uop_reg.rs1_data,
      A1_PC.value.U(SZ_A1.W)   -> uop_reg.pc
    )
  )

  val src2 = MuxLookup(ctrl.sel2, 0.U(p(XLen).W))(
    Seq(
      A2_ZERO.value.U(SZ_A2.W)   -> 0.U(p(XLen).W),
      A2_RS2.value.U(SZ_A2.W)    -> uop_reg.rs2_data,
      A2_IMM.value.U(SZ_A2.W)    -> imm_utils.genImm(uop_reg.instr, uop_reg.imm_type),
      A2_PCSTEP.value.U(SZ_A2.W) -> p(IAlign).U(p(XLen).W)
    )
  )

  alu.en   := valid
  alu.src1 := src1
  alu.src2 := src2
  alu.fn   := ctrl.fn
  alu.mode := ctrl.mode

  io.resp.valid        := valid
  io.resp.bits.result  := alu.result
  io.resp.bits.rd      := uop_reg.rd
  io.resp.bits.pc      := uop_reg.pc
  io.resp.bits.instr   := uop_reg.instr
  io.resp.bits.rob_tag := uop_reg.rob_tag
}
