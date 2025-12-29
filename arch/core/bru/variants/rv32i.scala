package arch.core.bru

import arch.core.common.Consts
import chisel3.util.BitPat

trait RV32IBranchConsts extends Consts {
  def BR_X   = BitPat("b???")
  val SZ_BR  = BR_X.getWidth
  def BR_EQ  = BitPat("b000")
  def BR_NE  = BitPat("b001")
  def BR_J   = BitPat("b010")
  def BR_N   = BitPat("b011")
  def BR_LT  = BitPat("b100")
  def BR_GE  = BitPat("b101")
  def BR_LTU = BitPat("b110")
  def BR_GEU = BitPat("b111")
}
