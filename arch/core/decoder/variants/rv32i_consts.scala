package arch.core.decoder

import arch.core.alu._
import chisel3.util.BitPat

trait RV32IDecodeConsts extends RV32IALUConsts {
  // Utility
  def X = BitPat("b?")
  def N = BitPat("b0")
  def Y = BitPat("b1")

  // Branch
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

  // Immediate
  def IMM_X  = BitPat("b???")
  val SZ_IMM = IMM_X.getWidth
  def IMM_I  = BitPat("b000")
  def IMM_S  = BitPat("b001")
  def IMM_B  = BitPat("b010")
  def IMM_U  = BitPat("b011")
  def IMM_J  = BitPat("b100")

  // LSU
  def M_X   = BitPat("b???")
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
