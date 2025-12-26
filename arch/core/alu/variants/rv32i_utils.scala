package arch.core.alu

import arch.configs._
import chisel3._
import chisel3.util._

class RV32IALUUtilitiesImpl extends ALUUtilities with RV32IALUConsts {
  def fnTypeWidth: Int                                           = SZ_AFN
  def fn(src1: UInt, src2: UInt, fnType: UInt, mode: Bool): UInt = {
    val result = Wire(UInt(p(XLen).W))

    // ADD/SUB
    val src2_inv  = Mux(mode, ~src2, src2)
    val adder_out = src1 + src2_inv + mode.asUInt

    // SRL/SRA
    val shift_right_out = Mux(
      mode,
      (src1.asSInt >> src2(4, 0)).asUInt,
      src1 >> src2(4, 0)
    )

    result := MuxLookup(fnType, 0.U(SZ_AFN.W))(
      Seq(
        AFN_ADD.value.U(SZ_AFN.W)  -> adder_out,
        AFN_SLL.value.U(SZ_AFN.W)  -> (src1 << src2(4, 0)),
        AFN_SLT.value.U(SZ_AFN.W)  -> Mux(src1.asSInt < src2.asSInt, 1.U, 0.U),
        AFN_SLTU.value.U(SZ_AFN.W) -> Mux(src1 < src2, 1.U, 0.U),
        AFN_XOR.value.U(SZ_AFN.W)  -> (src1 ^ src2),
        AFN_SRL.value.U(SZ_AFN.W)  -> shift_right_out,
        AFN_OR.value.U(SZ_AFN.W)   -> (src1 | src2),
        AFN_AND.value.U(SZ_AFN.W)  -> (src1 & src2)
      )
    )

    result
  }
}

object RV32IALUUtilities extends RegisteredALUUtilities with RV32IALUConsts {
  override def isaName: String     = "rv32i"
  override def utils: ALUUtilities = new RV32IALUUtilitiesImpl
}
