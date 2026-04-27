package arch.core.lsu

import arch.configs._
import chisel3._
import chisel3.util.{ Cat, Fill, MuxCase, log2Ceil }

trait MemoryDataHelpers extends Utils {
  def bytes(implicit p: Parameters): Int = p(XLen) / 8

  def byteOffsetWidth(implicit p: Parameters): Int =
    log2Ceil(bytes)

  def alignedAddr(addr: UInt)(implicit p: Parameters): UInt = {
    val offW = byteOffsetWidth
    Cat(addr(p(XLen) - 1, offW), 0.U(offW.W))
  }

  def byteOffset(addr: UInt)(implicit p: Parameters): UInt =
    addr(byteOffsetWidth - 1, 0)

  def expandByteMask(mask: UInt)(implicit p: Parameters): UInt =
    Cat((bytes - 1 to 0 by -1).map(i => Fill(8, mask(i))))
}

trait LoadUtils extends MemoryDataHelpers {
  def decodeLoad(uop: UInt): LoadCtrl

  def rawLoadMask(ctrl: LoadCtrl)(implicit p: Parameters): UInt = {
    val n = bytes

    MuxCase(
      Fill(n, 1.U(1.W)).asUInt,
      Seq(
        ctrl.is_byte  -> "b0001".U(n.W),
        ctrl.is_half  -> "b0011".U(n.W),
        ctrl.is_word  -> "b1111".U(n.W),
        ctrl.is_dword -> Fill(n, 1.U(1.W)).asUInt
      )
    )
  }

  def shiftedLoadMask(ctrl: LoadCtrl, addr: UInt)(implicit p: Parameters): UInt = {
    val n = bytes
    (rawLoadMask(ctrl) << byteOffset(addr))(n - 1, 0)
  }

  def loadResult(ctrl: LoadCtrl, addr: UInt, alignedData: UInt)(implicit p: Parameters): UInt = {
    val shifted = alignedData >> (byteOffset(addr) << 3)

    MuxCase(
      shifted,
      Seq(
        ctrl.is_byte  -> Cat(
          Fill(p(XLen) - 8, !ctrl.is_unsigned && shifted(7)),
          shifted(7, 0)
        ),
        ctrl.is_half  -> Cat(
          Fill(p(XLen) - 16, !ctrl.is_unsigned && shifted(15)),
          shifted(15, 0)
        ),
        ctrl.is_word  -> {
          if (p(XLen) == 64)
            Cat(Fill(p(XLen) - 32, !ctrl.is_unsigned && shifted(31)), shifted(31, 0))
          else
            shifted
        },
        ctrl.is_dword -> shifted
      )
    )
  }
}

trait StoreUtils extends MemoryDataHelpers {
  def decodeStore(uop: UInt): StoreCtrl

  def rawStoreMask(ctrl: StoreCtrl)(implicit p: Parameters): UInt = {
    val n = bytes

    MuxCase(
      Fill(n, 1.U(1.W)).asUInt,
      Seq(
        ctrl.is_byte  -> "b0001".U(n.W),
        ctrl.is_half  -> "b0011".U(n.W),
        ctrl.is_word  -> "b1111".U(n.W),
        ctrl.is_dword -> Fill(n, 1.U(1.W)).asUInt
      )
    )
  }

  def shiftedStoreMask(ctrl: StoreCtrl, addr: UInt)(implicit p: Parameters): UInt = {
    val n = bytes
    (rawStoreMask(ctrl) << byteOffset(addr))(n - 1, 0)
  }

  def narrowStoreData(ctrl: StoreCtrl, data: UInt)(implicit p: Parameters): UInt =
    MuxCase(
      data,
      Seq(
        ctrl.is_byte  -> Cat(Fill(p(XLen) - 8, 0.U), data(7, 0)),
        ctrl.is_half  -> Cat(Fill(p(XLen) - 16, 0.U), data(15, 0)),
        ctrl.is_word  -> {
          if (p(XLen) == 64) Cat(Fill(p(XLen) - 32, 0.U), data(31, 0))
          else data
        },
        ctrl.is_dword -> data
      )
    )

  def alignedStoreData(ctrl: StoreCtrl, addr: UInt, data: UInt)(implicit p: Parameters): UInt = {
    val raw = narrowStoreData(ctrl, data)
    (raw << (byteOffset(addr) << 3))(p(XLen) - 1, 0)
  }
}

object LoadUtilsFactory  extends UtilsFactory[LoadUtils]("LOAD")
object StoreUtilsFactory extends UtilsFactory[StoreUtils]("STORE")

object LoadStoreInit {
  val rv32iLoadUtils   = riscv.RV32ILoadUtils
  val rv32iStoreUtils  = riscv.RV32IStoreUtils
  val rv32imLoadUtils  = riscv.RV32IMLoadUtils
  val rv32imStoreUtils = riscv.RV32IMStoreUtils
}
