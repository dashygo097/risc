package arch.core.csr

import arch.core.common.Consts
import chisel3.util.BitPat

trait RV32ICsrConsts extends Consts {
  def C_X  = BitPat("b??")
  def SZ_C = C_X.getWidth
  def C_RW = BitPat("b00")
  def C_RS = BitPat("b01")
  def C_RC = BitPat("b10")
}

trait RV32ICsrMap extends Consts {
  def CSR_X  = BitPat("????_????_????")
  def SZ_CSR = CSR_X.getWidth
}

class RV32ICsrUtilitiesImpl extends CsrUtilities with RV32ICsrConsts with RV32ICsrMap {
  def cmdWidth: Int  = SZ_C
  def addrWidth: Int = SZ_CSR
}

object RV32ICsrUtilities extends RegisteredCsrUtilities {
  override def isaName: String     = "rv32i"
  override def utils: CsrUtilities = new RV32ICsrUtilitiesImpl
}
