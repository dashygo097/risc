package arch.core.bru

import arch.core.common.Consts
import chisel3.util._

trait RV32IBranchConsts extends Consts {
  def BR_X   = BitPat("b???")
  val SZ_BR  = BR_X.getWidth
  def BR_EQ  = BitPat("b000")
  def BR_NE  = BitPat("b001")
  def BR_LT  = BitPat("b010")
  def BR_GE  = BitPat("b011")
  def BR_LTU = BitPat("b100")
  def BR_GEU = BitPat("b101")
}

class RV32BruUtilitiesImpl extends BruUtilities with RV32IBranchConsts {
  def branchTypeWidth: Int = SZ_BR
}

object RV32BruUtilities extends RegisteredBruUtilities with RV32IBranchConsts {
  override def isaName: String     = "rv32i"
  override def utils: BruUtilities = new RV32BruUtilitiesImpl
}
