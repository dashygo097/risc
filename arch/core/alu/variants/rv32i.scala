package arch.core.alu

import arch.configs._
import chisel3._
import chisel3.util._

trait RV32IAluConsts extends AluConsts {
  def AFN_X    = BitPat("b????")
  val SZ_AFN   = AFN_X.getWidth
  def AFN_ADD  = BitPat("b0000")
  def AFN_SLL  = BitPat("b0001")
  def AFN_SLT  = BitPat("b0010")
  def AFN_SLTU = BitPat("b0011")
  def AFN_XOR  = BitPat("b0100")
  def AFN_SRL  = BitPat("b0101")
  def AFN_OR   = BitPat("b0110")
  def AFN_AND  = BitPat("b0111")

  def AFN_SNE  = BitPat("b1000")
  def AFN_SEQ  = BitPat("b1001")
  def AFN_BLT  = BitPat("b1010")
  def AFN_BLTU = BitPat("b1011")
  def AFN_BGE  = BitPat("b1100")
  def AFN_BGEU = BitPat("b1101")
}

class RV32IAluUtilitiesImpl(implicit p: Parameters) extends AluUtilities with RV32IAluConsts {
  def sel1Width: Int   = SZ_A1
  def sel2Width: Int   = SZ_A2
  def fnTypeWidth: Int = SZ_AFN

  def fn(src1: UInt, src2: UInt, fnType: UInt, mode: Bool): AluResult = {
    val result = Wire(new AluResult)

    val eq  = src1 === src2
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

    val cmp_result = MuxLookup(fnType, false.B)(
      Seq(
        AFN_SNE.value.U(SZ_AFN.W)  -> !eq,
        AFN_SEQ.value.U(SZ_AFN.W)  -> eq,
        AFN_BLT.value.U(SZ_AFN.W)  -> lt,
        AFN_BLTU.value.U(SZ_AFN.W) -> ltu,
        AFN_BGE.value.U(SZ_AFN.W)  -> !lt,
        AFN_BGEU.value.U(SZ_AFN.W) -> !ltu
      )
    )

    result.arith := Mux(isArithmetic(fnType), arith_result, adder_out)
    result.cmp   := cmp_result
    result
  }

  def isArithmetic(fnType: UInt): Bool = !fnType(3)
  def isComparison(fnType: UInt): Bool = fnType(3)
}

object RV32IAluUtilities extends RegisteredAluUtilities with RV32IAluConsts {
  override def isaName: String     = "rv32i"
  override def utils: AluUtilities = new RV32IAluUtilitiesImpl
}
