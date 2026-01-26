package arch.core.csr

import arch.core.common.Consts
import arch.configs._
import vopts.utils.Register
import chisel3._
import chisel3.util._

trait RV32ICsrConsts extends Consts {
  def C_X   = BitPat("b???")
  def SZ_C  = C_X.getWidth
  def C_RW  = BitPat("b000")
  def C_RS  = BitPat("b001")
  def C_RC  = BitPat("b010")
  def C_RWI = BitPat("b100")
  def C_RSI = BitPat("b101")
  def C_RCI = BitPat("b110")
}

trait RV32ICsrMap extends Consts {
  def CSR_U  = BitPat("b??00_????_????")
  def CSR_S  = BitPat("b??01_????_????")
  def CSR_H  = BitPat("b??10_????_????")
  def CSR_M  = BitPat("b??11_????_????")
  def SZ_CSR = CSR_U.getWidth

  // U-mode
  def CSR_CYCLE   = BitPat("b1100_0000_0000")
  def CSR_TIME    = BitPat("b1100_0000_0001")
  def CSR_INSTRET = BitPat("b1100_0000_0010")

  // S-mode
  def CSR_SSTATUS  = BitPat("b0001_0000_0000")
  def CSR_SIE      = BitPat("b0001_0000_0100")
  def CSR_STVEC    = BitPat("b0001_0000_0101")
  def CSR_SSCRATCH = BitPat("b0001_0100_0000")
  def CSR_SEPC     = BitPat("b0001_0100_0001")
  def CSR_SCAUSE   = BitPat("b0001_0100_0010")
  def CSR_SIP      = BitPat("b0001_0100_0100")
  def CSR_SATP     = BitPat("b0001_1000_0000")

  // H-mode

  // M-mode
  def CSR_MSTATUS   = BitPat("b0011_0000_0000")
  def CSR_MISA      = BitPat("b0011_0000_0001")
  def CSR_MIE       = BitPat("b0011_0000_0100")
  def CSR_MTVEC     = BitPat("b0011_0000_0101")
  def CSR_MSCRATCH  = BitPat("b0011_0100_0000")
  def CSR_MEPC      = BitPat("b0011_0100_0001")
  def CSR_MCAUSE    = BitPat("b0011_0100_0010")
  def CSR_MIP       = BitPat("b0011_0100_0100")
  def CSR_MCYCLE    = BitPat("b1011_0000_0000")
  def CSR_MINSTRET  = BitPat("b1011_0000_0010")
  def CSR_MVENDERID = BitPat("b1111_0001_0001")
  def CSR_MARCHID   = BitPat("b1111_0001_0010")
  def CSR_MIMPID    = BitPat("b1111_0001_0011")
  def CSR_MHARTID   = BitPat("b1111_0001_0100")
}

object RV32ICsrUtilities extends RegisteredUtilities[CsrUtilities] with RV32ICsrConsts with RV32ICsrMap {
  override def utils: CsrUtilities = new CsrUtilities {
    override def name: String = "rv32i"

    override def cmdWidth: Int              = SZ_C
    override def addrWidth: Int             = SZ_CSR
    override def immWidth: Int              = 5
    override def isImm(cmd: UInt): Bool     = cmd(2)
    override def genImm(imm: UInt): UInt    = Cat(
      Fill(27, 0.U),
      imm(4, 0)
    )
    override def getAddr(instr: UInt): UInt = instr(31, 20)

    override def fn(cmd: UInt, csr_data: UInt, src_data: UInt): UInt =
      MuxCase(
        csr_data,
        Seq(
          (cmd === C_RW)  -> src_data,
          (cmd === C_RS)  -> (csr_data | src_data),
          (cmd === C_RC)  -> (csr_data & ~src_data),
          (cmd === C_RWI) -> src_data,
          (cmd === C_RSI) -> (csr_data | src_data),
          (cmd === C_RCI) -> (csr_data & ~src_data)
        )
      )
    override def table: Seq[Register]                                = Seq(
      // U-mode CSRs
      Register("cycle", CSR_CYCLE.value, 0x0L, writable = false),
      Register("time", CSR_TIME.value, 0x0L, writable = false),
      Register("instret", CSR_INSTRET.value, 0x0L, writable = false),

      // S-mode CSRs
      Register("sstatus", CSR_SSTATUS.value, 0x0L),
      Register("sie", CSR_SIE.value, 0x0L),
      Register("stvec", CSR_STVEC.value, 0x0L),
      Register("sscratch", CSR_SSCRATCH.value, 0x0L),
      Register("sepc", CSR_SEPC.value, 0x0L),
      Register("scause", CSR_SCAUSE.value, 0x0L),
      Register("sip", CSR_SIP.value, 0x0L),
      Register("satp", CSR_SATP.value, 0x0L),

      // M-mode CSRs
      Register("mstatus", CSR_MSTATUS.value, 0x0L),
      Register("misa", CSR_MISA.value, 0x40000100L, writable = false),
      Register("mie", CSR_MIE.value, 0x0L),
      Register("mtvec", CSR_MTVEC.value, 0x0L),
      Register("mscratch", CSR_MSCRATCH.value, 0x0L),
      Register("mepc", CSR_MEPC.value, 0x0L),
      Register("mcause", CSR_MCAUSE.value, 0x0L),
      Register("mip", CSR_MIP.value, 0x0L),
      Register("mcycle", CSR_MCYCLE.value, 0x0L, writable = false),
      Register("minstret", CSR_MINSTRET.value, 0x0L, writable = false),
      Register("mvendorid", CSR_MVENDERID.value, 0x0L, writable = false),
      Register("marchid", CSR_MARCHID.value, 0x0L, writable = false),
      Register("mimpid", CSR_MIMPID.value, 0x0L, writable = false),
      Register("mhartid", CSR_MHARTID.value, 0x0L, writable = false)
    )
  }

  override def factory: UtilitiesFactory[CsrUtilities] = CsrUtilitiesFactory
}
