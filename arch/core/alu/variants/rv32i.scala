package arch.core.alu

import chisel3._
import chisel3.util._

trait RV32IAluConsts extends AluConsts {
  def AFN_X   = BitPat("b????")
  val SZ_AFN  = AFN_X.getWidth
  def AFN_ADD = BitPat("b0000")
  def AFN_SLL = BitPat("b0001")
  def AFN_XOR = BitPat("b0010")
  def AFN_SRL = BitPat("b0011")
  def AFN_OR  = BitPat("b0100")
  def AFN_AND = BitPat("b0101")

  def AFN_SNE  = BitPat("b1000")
  def AFN_SEQ  = BitPat("b1001")
  def AFN_SLT  = BitPat("b1010")
  def AFN_SLTU = BitPat("b1011")
  def AFN_SGE  = BitPat("b1100")
  def AFN_SGEU = BitPat("b1101")
}

class RV32IAluUtilitiesImpl extends AluUtilities with RV32IAluConsts {
  def sel1Width: Int   = SZ_A1
  def sel2Width: Int   = SZ_A2
  def fnTypeWidth: Int = SZ_AFN

  def fn(src1: UInt, src2: UInt, fnType: UInt, mode: Bool): UInt = {
    val xlen = src1.getWidth

    // Comparisons
    val eq  = src1 === src2
    val lt  = src1.asSInt < src2.asSInt
    val ltu = src1 < src2

    // Arithmetic
    val src2_inv  = Mux(mode, ~src2, src2)
    val adder_out = (src1 + src2_inv + mode.asUInt)(xlen - 1, 0)

    val shamt   = src2(4, 0)
    val sll_out = (src1 << shamt)(xlen - 1, 0)
    val srl_out = Mux(
      mode,
      (src1.asSInt >> shamt).asUInt,
      src1 >> shamt
    )(xlen - 1, 0)

    // Arithmetic result based on fnType[2:0]
    val arith_result = MuxLookup(fnType, 0.U(xlen.W))(
      Seq(
        AFN_ADD.value.U(SZ_AFN.W) -> adder_out,     // ADD/SUB
        AFN_SLL.value.U(SZ_AFN.W) -> sll_out,       // SLL
        AFN_XOR.value.U(SZ_AFN.W) -> (src1 ^ src2), // XOR
        AFN_SRL.value.U(SZ_AFN.W) -> srl_out,       // SRL/SRA
        AFN_OR.value.U(SZ_AFN.W)  -> (src1 | src2), // OR
        AFN_AND.value.U(SZ_AFN.W) -> (src1 & src2)  // AND
      )
    )

    // Comparison result based on fnType[2:0]
    val cmp_result = MuxLookup(fnType, false.B)(
      Seq(
        AFN_SNE.value.U(SZ_AFN.W)  -> !eq, // SNE: src1 != src2
        AFN_SEQ.value.U(SZ_AFN.W)  -> eq,  // SEQ: src1 == src2
        AFN_SLT.value.U(SZ_AFN.W)  -> lt,  // SLT: src1 < src2 (signed)
        AFN_SLTU.value.U(SZ_AFN.W) -> ltu, // SLTU: src1 < src2 (unsigned)
        AFN_SGE.value.U(SZ_AFN.W)  -> !lt, // SGE: src1 >= src2 (signed)
        AFN_SGEU.value.U(SZ_AFN.W) -> !ltu // SGEU: src1 >= src2 (unsigned)
      )
    ).asUInt

    Mux(isComparison(fnType), cmp_result, arith_result)
  }
  def isArithmetic(fnType: UInt): Bool                           =
    fnType(3) === 0.B
  def isComparison(fnType: UInt): Bool                           =
    fnType(3) === 1.B
}

object RV32IAluUtilities extends RegisteredAluUtilities with RV32IAluConsts {
  override def isaName: String     = "rv32i"
  override def utils: AluUtilities = new RV32IAluUtilitiesImpl
}
