package arch.core.alu

import arch.configs._
import chisel3._
import chisel3.util._

trait RV32IAluConsts extends AluConsts {
  def AFN_X    = BitPat("b???")
  val SZ_AFN   = AFN_X.getWidth
  def AFN_ADD  = BitPat("b000")
  def AFN_SLL  = BitPat("b001")
  def AFN_SLT  = BitPat("b010")
  def AFN_SLTU = BitPat("b011")
  def AFN_XOR  = BitPat("b100")
  def AFN_SRL  = BitPat("b101")
  def AFN_OR   = BitPat("b110")
  def AFN_AND  = BitPat("b111")
}

class RV32IAluUtilitiesImpl(implicit p: Parameters) extends AluUtilities with RV32IAluConsts {
  def sel1Width: Int   = SZ_A1
  def sel2Width: Int   = SZ_A2
  def fnTypeWidth: Int = SZ_AFN

  def fn(src1: UInt, src2: UInt, fnType: UInt, mode: Bool): UInt = {
    val lt  = src1.asSInt < src2.asSInt
    val ltu = src1 < src2

    val src2_inv  = Mux(mode, ~src2, src2)
    val adder_out = (src1 + src2_inv + mode.asUInt)(p(XLen) - 1, 0)

    val shamt   = src2(4, 0)
    val sll_out = (src1 << shamt)(p(XLen) - 1, 0)
    val srl_out = Mux(
      mode,
      (src1.asSInt >> shamt).asUInt,
      src1 >> shamt
    )(p(XLen) - 1, 0)

    val arith_result = MuxLookup(fnType, 0.U(p(XLen).W))(
      Seq(
        AFN_ADD.value.U(SZ_AFN.W)  -> adder_out,
        AFN_SLL.value.U(SZ_AFN.W)  -> sll_out,
        AFN_SLT.value.U(SZ_AFN.W)  -> lt.asUInt,
        AFN_SLTU.value.U(SZ_AFN.W) -> ltu.asUInt,
        AFN_XOR.value.U(SZ_AFN.W)  -> (src1 ^ src2),
        AFN_SRL.value.U(SZ_AFN.W)  -> srl_out,
        AFN_OR.value.U(SZ_AFN.W)   -> (src1 | src2),
        AFN_AND.value.U(SZ_AFN.W)  -> (src1 & src2)
      )
    )

    arith_result
  }
}

object RV32IAluUtilities extends RegisteredAluUtilities {
  override def isaName: String     = "rv32i"
  override def utils: AluUtilities = new RV32IAluUtilitiesImpl
}
