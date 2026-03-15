package arch.core.bpu

import chisel3.util.BitPat

trait BHTConsts {
  def BHT_X   = BitPat("b??")
  def SZ_BHT  = BHT_X.getWidth
  def BHT_SNT = BitPat("b00")
  def BHT_WNT = BitPat("b01")
  def BHT_WT  = BitPat("b10")
  def BHT_ST  = BitPat("b11")
}
