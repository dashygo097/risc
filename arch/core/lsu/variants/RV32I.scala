package arch.core.lsu.riscv

import arch.core.lsu._
import arch.configs._
import chisel3._
import chisel3.util.{ BitPat, MuxLookup }

// Format: uop[7:4] = 0 | uop[3] = is_read | uop[2] = is_unsigned | uop[1:0] = size
trait RV32ILsuUOpConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)
  private def N                         = BitPat("b0")
  private def Y                         = BitPat("b1")
  private def P_X                       = BitPat("b????")

  def L_X  = BitPat("b??")
  def SZ_L = L_X.getWidth
  def L_B  = BitPat("b00") // Byte
  def L_H  = BitPat("b01") // Half
  def L_W  = BitPat("b10") // Word

  def UOP_LB  = cat(P_X, Y, N, L_B)
  def UOP_LH  = cat(P_X, Y, N, L_H)
  def UOP_LW  = cat(P_X, Y, N, L_W)
  def UOP_LBU = cat(P_X, Y, Y, L_B)
  def UOP_LHU = cat(P_X, Y, Y, L_H)
  def UOP_SB  = cat(P_X, N, N, L_B)
  def UOP_SH  = cat(P_X, N, N, L_H)
  def UOP_SW  = cat(P_X, N, N, L_W)
}

object RV32ILsuUtils extends RegisteredUtils[LsuUtils] with RV32ILsuUOpConsts {
  override def utils: LsuUtils = new LsuUtils {
    override def name: String = "rv32i"

    override def decode(uop: UInt): LsuCtrl = {
      val ctrl = Wire(new LsuCtrl)
      val size = uop(1, 0)

      ctrl.is_byte     := size === L_B.value.U
      ctrl.is_half     := size === L_H.value.U
      ctrl.is_word     := size === L_W.value.U
      ctrl.is_unsigned := uop(2)
      ctrl.is_read     := uop(3)
      ctrl.is_write    := !uop(3)

      ctrl.strb := MuxLookup(size, "b0000".U(4.W))(
        Seq(
          L_B.value.U(SZ_L.W) -> "b0001".U(4.W),
          L_H.value.U(SZ_L.W) -> "b0011".U(4.W),
          L_W.value.U(SZ_L.W) -> "b1111".U(4.W)
        )
      )
      ctrl
    }
  }

  override def factory: UtilsFactory[LsuUtils] = LsuUtilsFactory
}
