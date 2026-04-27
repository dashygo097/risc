package arch.core.lsu.riscv

import arch.core.lsu._
import arch.configs._
import chisel3._
import chisel3.util.{ BitPat, MuxLookup }

// uop[7:4] = unused | uop[3] = is_load | uop[2] = is_unsigned | uop[1:0] = size
trait RV32IMemUOpConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)

  private def N   = BitPat("b0")
  private def Y   = BitPat("b1")
  private def P_X = BitPat("b????")

  def MEM_X  = BitPat("b??")
  def SZ_MEM = MEM_X.getWidth

  def MEM_B = BitPat("b00")
  def MEM_H = BitPat("b01")
  def MEM_W = BitPat("b10")

  def UOP_LB  = cat(P_X, Y, N, MEM_B)
  def UOP_LH  = cat(P_X, Y, N, MEM_H)
  def UOP_LW  = cat(P_X, Y, N, MEM_W)
  def UOP_LBU = cat(P_X, Y, Y, MEM_B)
  def UOP_LHU = cat(P_X, Y, Y, MEM_H)

  def UOP_SB = cat(P_X, N, N, MEM_B)
  def UOP_SH = cat(P_X, N, N, MEM_H)
  def UOP_SW = cat(P_X, N, N, MEM_W)
}

object RV32ILoadUtils extends RegisteredUtils[LoadUtils] with RV32IMemUOpConsts {
  override def utils: LoadUtils = new LoadUtils {
    override def name: String = "rv32i"

    override def decodeLoad(uop: UInt): LoadCtrl = {
      val ctrl = Wire(new LoadCtrl)
      val size = uop(1, 0)

      ctrl.is_byte     := size === MEM_B.value.U(SZ_MEM.W)
      ctrl.is_half     := size === MEM_H.value.U(SZ_MEM.W)
      ctrl.is_word     := size === MEM_W.value.U(SZ_MEM.W)
      ctrl.is_dword    := false.B
      ctrl.is_unsigned := uop(2)

      ctrl.strb := MuxLookup(size, "b0000".U(4.W))(
        Seq(
          MEM_B.value.U(SZ_MEM.W) -> "b0001".U(4.W),
          MEM_H.value.U(SZ_MEM.W) -> "b0011".U(4.W),
          MEM_W.value.U(SZ_MEM.W) -> "b1111".U(4.W)
        )
      )

      ctrl
    }
  }

  override def factory: UtilsFactory[LoadUtils] = LoadUtilsFactory
}

object RV32IStoreUtils extends RegisteredUtils[StoreUtils] with RV32IMemUOpConsts {
  override def utils: StoreUtils = new StoreUtils {
    override def name: String = "rv32i"

    override def decodeStore(uop: UInt): StoreCtrl = {
      val ctrl = Wire(new StoreCtrl)
      val size = uop(1, 0)

      ctrl.is_byte  := size === MEM_B.value.U(SZ_MEM.W)
      ctrl.is_half  := size === MEM_H.value.U(SZ_MEM.W)
      ctrl.is_word  := size === MEM_W.value.U(SZ_MEM.W)
      ctrl.is_dword := false.B

      ctrl.strb := MuxLookup(size, "b0000".U(4.W))(
        Seq(
          MEM_B.value.U(SZ_MEM.W) -> "b0001".U(4.W),
          MEM_H.value.U(SZ_MEM.W) -> "b0011".U(4.W),
          MEM_W.value.U(SZ_MEM.W) -> "b1111".U(4.W)
        )
      )

      ctrl
    }
  }

  override def factory: UtilsFactory[StoreUtils] = StoreUtilsFactory
}
