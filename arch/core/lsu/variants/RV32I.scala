package arch.core.lsu

import arch.configs._
import chisel3._
import chisel3.util.{ BitPat, MuxLookup }

// Format: uop[7:4] = 0 | uop[3] = is_read | uop[2] = is_unsigned | uop[1:0] = size
trait RV32ILsuUOpConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)
  private def P_X                       = BitPat("b????")

  def L_RD = BitPat("b1") // Read
  def L_WR = BitPat("b0") // Write

  def L_U = BitPat("b1") // Unsigned
  def L_S = BitPat("b0") // Signed

  def L_B = BitPat("b00") // Byte
  def L_H = BitPat("b01") // Half
  def L_W = BitPat("b10") // Word

  def UOP_LB  = cat(P_X, L_RD, L_S, L_B)
  def UOP_LH  = cat(P_X, L_RD, L_S, L_H)
  def UOP_LW  = cat(P_X, L_RD, L_S, L_W)
  def UOP_LBU = cat(P_X, L_RD, L_U, L_B)
  def UOP_LHU = cat(P_X, L_RD, L_U, L_H)
  def UOP_SB  = cat(P_X, L_WR, L_S, L_B)
  def UOP_SH  = cat(P_X, L_WR, L_S, L_H)
  def UOP_SW  = cat(P_X, L_WR, L_S, L_W)
}

object RV32ILsuUtilities extends RegisteredUtilities[LsuUtilities] with RV32ILsuUOpConsts {
  override def utils: LsuUtilities = new LsuUtilities {
    override def name: String = "rv32i"

    override def decodeUop(uop: UInt): LsuCtrl = {
      val ctrl = Wire(new LsuCtrl)
      val size = uop(1, 0)

      ctrl.is_byte     := size === "b00".U
      ctrl.is_half     := size === "b01".U
      ctrl.is_word     := size === "b10".U
      ctrl.is_unsigned := uop(2)
      ctrl.is_read     := uop(3)
      ctrl.is_write    := !uop(3)

      ctrl.strb := MuxLookup(size, "b0000".U(4.W))(
        Seq(
          "b00".U -> "b0001".U(4.W),
          "b01".U -> "b0011".U(4.W),
          "b10".U -> "b1111".U(4.W)
        )
      )
      ctrl
    }
  }

  override def factory: UtilitiesFactory[LsuUtilities] = LsuUtilitiesFactory
}
