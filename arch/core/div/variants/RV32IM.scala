package arch.core.div

import arch.configs._
import chisel3._
import chisel3.util.Fill
import vopts.math.RestoringDivider
import chisel3.util.BitPat

trait RV32IMDivUOpConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)
  private def P_X                       = BitPat("b??????")

  def DR_X = BitPat("b?")
  def DR_0 = BitPat("b0") // quotient
  def DR_1 = BitPat("b1") // remainder
  def DS_X = BitPat("b?")
  def DS_0 = BitPat("b0") // unsigned
  def DS_1 = BitPat("b1") // signed

  // Format: uop[7:2] = 0 | uop[1] = signed_bit | uop[0] = rem_bit
  def UOP_DIV  = cat(P_X, DS_1, DR_0)
  def UOP_DIVU = cat(P_X, DS_0, DR_0)
  def UOP_REM  = cat(P_X, DS_1, DR_1)
  def UOP_REMU = cat(P_X, DS_0, DR_1)
}

object RV32IMDivUtils extends RegisteredUtils[DivUtils] with RV32IMDivUOpConsts {
  override def utils: DivUtils = new DivUtils {
    override def name: String = "rv32im"

    override def decode(uop: UInt): DivCtrl = {
      val ctrl = Wire(new DivCtrl)
      ctrl.is_signed := uop(1)
      ctrl.is_rem    := uop(0)
      ctrl
    }

    override def fn(en: Bool, kill: Bool, src1: UInt, src2: UInt, is_signed: Bool, is_rem: Bool): (UInt, Bool, Bool) = {
      val result = WireDefault(0.U(p(XLen).W))
      val busy = WireDefault(false.B)
      val done = WireDefault(true.B)

      val div = Module(new RestoringDivider(p(XLen)))

      val int_min = (1.U(p(XLen).W) << (p(XLen) - 1))
      val minus_one = (-1.S(p(XLen).W)).asUInt
      val is_div_by_zero = src2 === 0.U
      val is_signed_overflow = is_signed && (src1 === int_min) && (src2 === minus_one)
      val special_case = is_div_by_zero || is_signed_overflow

      val special_quotient = Wire(UInt(p(XLen).W))
      val special_remainder = Wire(UInt(p(XLen).W))

      special_quotient := Mux(
        is_div_by_zero,
        Fill(p(XLen), 1.U(1.W)),
        src1
      )

      special_remainder := Mux(
        is_div_by_zero,
        src1,
        0.U(p(XLen).W)
      )

      div.start := en && !special_case
      div.kill := kill
      div.dividend := src1
      div.divisor := src2
      div.is_signed := is_signed
    div.select_remainder := is_rem

      result := Mux(
        special_case,
        Mux(is_rem, special_remainder, special_quotient),
        div.result
      )
      busy := Mux(special_case, false.B, div.busy)
      done := Mux(special_case, true.B, div.done)

      (result, busy, done)
    }
  }

  override def factory: UtilsFactory[DivUtils] = DivUtilsFactory
}
