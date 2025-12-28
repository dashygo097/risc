package arch.core.common

import chisel3.util.BitPat

trait Consts {
  def X = BitPat("b?")
  def N = BitPat("b0")
  def Y = BitPat("b1")
}
