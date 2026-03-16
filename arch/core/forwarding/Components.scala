package arch.core.forwarding

import chisel3.util._

trait ForwardingConsts {
  def FWD_X  = BitPat("b??")
  def SZ_FWD = FWD_X.getWidth

  def FWD_SAFE = BitPat("b00")
  def FWD_EX   = BitPat("b01")
  def FWD_MEM  = BitPat("b10")
  def FWD_WB   = BitPat("b11")
}
