package arch.core.lsu

import arch.core.common.Consts
import chisel3._
import chisel3.util._

trait RV32ILsuConsts extends Consts {
  def M_X  = BitPat("b????")
  val SZ_M = M_X.getWidth
  def M_SB = BitPat("b0000")
  def M_SH = BitPat("b0001")
  def M_SW = BitPat("b0010")

  def M_LB  = BitPat("b1000")
  def M_LH  = BitPat("b1001")
  def M_LW  = BitPat("b1010")
  def M_LBU = BitPat("b1100")
  def M_LHU = BitPat("b1101")
}

class RV32ILsuUtilitiesImpl extends LsuUtilities with RV32ILsuConsts {
  def cmdWidth: Int   = SZ_M
  def strb(cmd: UInt) =
    MuxLookup(cmd(1, 0), "b0000".U(4.W))(
      Seq(
        "b00".U -> "b0001".U(4.W),
        "b01".U -> "b0011".U(4.W),
        "b10".U -> "b1111".U(4.W)
      )
    )

  def isByte(cmd: UInt): Bool                   = cmd(1, 0) === "b00".U
  def isHalf(cmd: UInt): Bool                   = cmd(1, 0) === "b01".U
  def isWord(cmd: UInt): Bool                   = cmd(1, 0) === "b10".U
  def isUnsigned(cmd: UInt): Bool               = cmd(2)
  def isRead(cmd: UInt): Bool                   = cmd(3)
  def isWrite(cmd: UInt): Bool                  = !cmd(3)
  def isMemRead(is_mem: Bool, cmd: UInt): Bool  = is_mem && cmd(3)
  def isMemWrite(is_mem: Bool, cmd: UInt): Bool = is_mem && !cmd(3)
}

object RV32ILsuUtilities extends RegisteredLsuUtilities {
  override def isaName: String     = "rv32i"
  override def utils: LsuUtilities = new RV32ILsuUtilitiesImpl
}
