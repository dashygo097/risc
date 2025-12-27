package arch.core.regfile

import utils.Register

class RV32IRegfileUtilitiesImpl extends RegfileUtilities {
  def numGPR: Int              = 32
  def extraInfo: Seq[Register] =
    Seq(
      Register("x0", 0x0, 0x0L, writable = false, readable = true),
    )
}

object RV32IRegfileUtilities extends RegisteredRegfileUtilities {
  override def isaName: String         = "rv32i"
  override def utils: RegfileUtilities = new RV32IRegfileUtilitiesImpl
}
