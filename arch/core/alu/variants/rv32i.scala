package arch.core.alu

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

class RV32IAluUtilitiesImpl extends AluUtilities with RV32IAluConsts {
  def sel1Width: Int   = SZ_A1
  def sel2Width: Int   = SZ_A2
  def fnTypeWidth: Int = SZ_AFN

  def fn(src1: UInt, src2: UInt, fnType: UInt, mode: Bool): UInt = {
    val xlen = src1.getWidth

    // ADD/SUB
    val src2_inv  = Mux(mode, ~src2, src2)
    val adder_out = (src1 + src2_inv + mode.asUInt)(xlen - 1, 0)

    // SRL/SRA
    val shamt           = src2(4, 0)
    val shift_right_out = Mux(
      mode,
      (src1.asSInt >> shamt).asUInt,
      src1 >> shamt
    )(xlen - 1, 0)

    // SLL
    val shift_left_out = (src1 << shamt)(xlen - 1, 0)

    // Comparisons
    val slt_out  = (src1.asSInt < src2.asSInt).asUInt
    val sltu_out = (src1 < src2).asUInt

    MuxLookup(fnType, 0.U(xlen.W))(
      Seq(
        AFN_ADD.value.U  -> adder_out,
        AFN_SLL.value.U  -> shift_left_out,
        AFN_SLT.value.U  -> slt_out,
        AFN_SLTU.value.U -> sltu_out,
        AFN_XOR.value.U  -> (src1 ^ src2),
        AFN_SRL.value.U  -> shift_right_out,
        AFN_OR.value.U   -> (src1 | src2),
        AFN_AND.value.U  -> (src1 & src2)
      )
    )
  }
}

object RV32IAluUtilities extends RegisteredAluUtilities with RV32IAluConsts {
  override def isaName: String     = "rv32i"
  override def utils: AluUtilities = new RV32IAluUtilitiesImpl
}
