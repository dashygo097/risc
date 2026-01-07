package arch.core.csr

import arch.core.common.Consts
import arch.configs._
import chisel3.util.BitPat

trait RV32ICsrConsts extends Consts {
  def C_X  = BitPat("b??")
  def SZ_C = C_X.getWidth
  def C_RW = BitPat("b00")
  def C_RS = BitPat("b01")
  def C_RC = BitPat("b10")
}

trait RV32ICsrMap extends Consts {
  def CSR_U  = BitPat("b??00_????_????")
  def CSR_S  = BitPat("b??01_????_????")
  def CSR_H  = BitPat("b??10_????_????")
  def CSR_M  = BitPat("b??11_????_????")
  def SZ_CSR = CSR_U.getWidth

  // U-mode
  def CSR_CYCLE   = BitPat("hC00")
  def CSR_TIME    = BitPat("hC01")
  def CSR_INSTRET = BitPat("hC02")

  // S-mode
  def CSR_SSTATUS  = BitPat("h100")
  def CSR_SIE      = BitPat("h104")
  def CSR_STVEC    = BitPat("h105")
  def CSR_SSCRATCH = BitPat("h140")
  def CSR_SEPC     = BitPat("h141")
  def CSR_SCAUSE   = BitPat("h142")
  def CSR_SIP      = BitPat("h144")
  def CSR_SATP     = BitPat("h180")

  // H-mode

  // M-mode
  def CSR_MSTATUS   = BitPat("h300")
  def CSR_MISA      = BitPat("h301")
  def CSR_MIE       = BitPat("h304")
  def CSR_MTVEC     = BitPat("h305")
  def CSR_MSCRATCH  = BitPat("h340")
  def CSR_MEPC      = BitPat("h341")
  def CSR_MCAUSE    = BitPat("h342")
  def CSR_MIP       = BitPat("h344")
  def CSR_MCYCLE    = BitPat("hB00")
  def CSR_MINSTRET  = BitPat("hB02")
  def CSR_MVENDERID = BitPat("hF11")
  def CSR_MARCHID   = BitPat("hF12")
  def CSR_MIMPID    = BitPat("hF13")
  def CSR_MHARTID   = BitPat("hF14")
}

object RV32ICsrUtilities extends RegisteredUtilities[CsrUtilities] with RV32ICsrConsts with RV32ICsrMap {
  override def utils: CsrUtilities = new CsrUtilities {
    override def name: String = "rv32i"

    override def cmdWidth: Int  = SZ_C
    override def addrWidth: Int = SZ_CSR
  }

  override def factory: UtilitiesFactory[CsrUtilities] = CsrUtilitiesFactory
}
