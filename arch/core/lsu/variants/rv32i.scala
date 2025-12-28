package arch.core.lsu

import arch.core.common.Consts
import chisel3.util._

trait RV32ILsuConsts extends Consts {
  def M_X   = BitPat("b????")
  val SZ_M  = M_X.getWidth
  def M_SB  = BitPat("b000")
  def M_SH  = BitPat("b001")
  def M_SW  = BitPat("b010")
  def M_LB  = BitPat("b011")
  def M_LH  = BitPat("b100")
  def M_LW  = BitPat("b101")
  def M_LBU = BitPat("b110")
  def M_LHU = BitPat("b111")
}

class RV32ILsuUtilitiesImpl extends LsuUtilities with RV32ILsuConsts {
  def cmdWidth: Int = SZ_M
}

object RV32ILsuUtilities extends RegisteredLsuUtilities with RV32ILsuConsts {
  override def isaName: String     = "rv32i"
  override def utils: LsuUtilities = new RV32ILsuUtilitiesImpl
}
