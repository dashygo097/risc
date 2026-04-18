package arch.core.mult.riscv

import arch.core.mult._
import arch.configs._
import vopts.math.Multiplier
import chisel3._
import chisel3.util.BitPat

// Format: uop[7:3] = 0 | uop[2] = signed_bit | uop[1] = signed_bit | uop[0] = bit_sel
trait RV32IMMultUOpConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)
  private def P_X                       = BitPat("b?????")

  def MH_X = BitPat("b?")
  def MH_0 = BitPat("b0") // Low 32 bits
  def MH_1 = BitPat("b1") // High 32 bits
  def MS_X = BitPat("b?")
  def MS_0 = BitPat("b0") // Unsigned
  def MS_1 = BitPat("b1") // Signed

  def UOP_MUL    = cat(P_X, MS_1, MS_1, MH_0)
  def UOP_MULH   = cat(P_X, MS_1, MS_1, MH_1)
  def UOP_MULHSU = cat(P_X, MS_1, MS_0, MH_1)
  def UOP_MULHU  = cat(P_X, MS_0, MS_0, MH_1)
}

object RV32IMMultUtils extends RegisteredUtils[MultUtils] with RV32IMMultUOpConsts {
  override def utils: MultUtils = new MultUtils {
    override def name: String = "rv32im"

    override def decode(uop: UInt): MultCtrl = {
      val ctrl = Wire(new MultCtrl)
      ctrl.a_signed := uop(2)
      ctrl.b_signed := uop(1)
      ctrl.high     := uop(0)
      ctrl
    }

    override def fn(en: Bool, kill: Bool, src1: UInt, src2: UInt, a_signed: Bool, b_signed: Bool, high: Bool): (UInt, Bool, Bool) = {
      val result = WireDefault(0.U(p(XLen).W))
      val busy   = WireDefault(false.B)
      val done   = WireDefault(true.B)

      val mult = Module(new Multiplier(p(XLen), p(MultPipelineStages)))

      mult.start        := en
      mult.kill         := kill
      mult.multiplicand := src1
      mult.multiplier   := src2
      mult.a_signed     := a_signed
      mult.b_signed     := b_signed
      mult.take_high    := high

      result := mult.result
      done   := mult.done
      busy   := mult.busy

      (result, busy, done)
    }
  }

  override def factory: UtilsFactory[MultUtils] = MultUtilsFactory
}
