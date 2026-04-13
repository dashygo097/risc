package arch.core.csr

import arch.configs._
import vopts.utils.Register
import chisel3._
import chisel3.util.{ BitPat, MuxCase, Cat, Fill }

trait RV32ICsrConsts {
  def C_X   = BitPat("b???")
  def SZ_C  = C_X.getWidth
  def C_RW  = BitPat("b000")
  def C_RS  = BitPat("b001")
  def C_RC  = BitPat("b010")
  def C_RWI = BitPat("b100")
  def C_RSI = BitPat("b101")
  def C_RCI = BitPat("b110")
}

trait RV32ICsrMap {
  def CSR_U  = BitPat("b??00_????_????")
  def CSR_S  = BitPat("b??01_????_????")
  def CSR_H  = BitPat("b??10_????_????")
  def CSR_M  = BitPat("b??11_????_????")
  def SZ_CSR = CSR_U.getWidth

  // U-mode
  def CSR_CYCLE    = BitPat("b1100_0000_0000")
  def CSR_INSTRET  = BitPat("b1100_0000_0010")
  def CSR_CYCLEH   = BitPat("b1100_1000_0000")
  def CSR_INSTRETH = BitPat("b1100_1000_0010")

  // S-mode

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
  def CSR_MCYCLEH   = BitPat("b1011_1000_0000")
  def CSR_MINSTRETH = BitPat("b1011_1000_0010")
}

object RV32ICsrUtilities extends RegisteredUtilities[CsrUtilities] with RV32ICsrConsts with RV32ICsrMap {
  override def utils: CsrUtilities = new CsrUtilities {
    override def name: String = "rv32i"

    override def cmdWidth: Int              = SZ_C
    override def addrWidth: Int             = SZ_CSR
    override def immWidth: Int              = 5
    override def isImm(cmd: UInt): Bool     = cmd(2)
    override def genImm(imm: UInt): UInt    = Cat(Fill(27, 0.U), imm(4, 0))
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

    override def extraInputs: Seq[(String, Int)] = Seq(
      "cycle"   -> 64,
      "instret" -> 64,

      // Interrupts
      "timer_irq" -> 1,
      "soft_irq"  -> 1,
      "ext_irq"   -> 1
    )

    override def table: Seq[(Register, CsrUpdateBehavior)] = Seq(
      // U-mode
      (Register("cycle", CSR_CYCLE.value, 0x0L, writable = false), AlwaysUpdate(params => params("cycle")(31, 0))),
      (Register("instret", CSR_INSTRET.value, 0x0L, writable = false), AlwaysUpdate(params => params("instret")(31, 0))),
      (Register("cycleh", CSR_CYCLEH.value, 0x0L, writable = false), AlwaysUpdate(params => params("cycle")(63, 32))),
      (Register("instreth", CSR_INSTRETH.value, 0x0L, writable = false), AlwaysUpdate(params => params("instret")(63, 32))),

      // M-mode
      (Register("mstatus", CSR_MSTATUS.value, 0x0L), NormalUpdate),
      (Register("misa", CSR_MISA.value, 0x40000100L, writable = false), NormalUpdate),
      (Register("mie", CSR_MIE.value, 0x0L), NormalUpdate),
      (Register("mtvec", CSR_MTVEC.value, 0x0L), NormalUpdate),
      (Register("mscratch", CSR_MSCRATCH.value, 0x0L), NormalUpdate),
      (Register("mepc", CSR_MEPC.value, 0x0L), NormalUpdate),
      (Register("mcause", CSR_MCAUSE.value, 0x0L), NormalUpdate),
      (
        Register("mip", CSR_MIP.value, 0x0L, writable = false),
        AlwaysUpdate { params =>
          val meip = params("ext_irq")   // bit 11
          val mtip = params("timer_irq") // bit 7
          val msip = params("soft_irq")  // bit 3
          (meip << 11) | (mtip << 7) | (msip << 3)
        }
      ),
      (Register("mcycle", CSR_MCYCLE.value, 0x0L, writable = false), AlwaysUpdate(params => params("cycle")(31, 0))),
      (Register("minstret", CSR_MINSTRET.value, 0x0L, writable = false), AlwaysUpdate(params => params("instret")(31, 0))),
      (Register("mvendorid", CSR_MVENDERID.value, 0x0L, writable = false), NormalUpdate),
      (Register("marchid", CSR_MARCHID.value, 0x0L, writable = false), NormalUpdate),
      (Register("mimpid", CSR_MIMPID.value, 0x0L, writable = false), NormalUpdate),
      (Register("mhartid", CSR_MHARTID.value, 0x0L, writable = false), NormalUpdate),
      (Register("mcycleh", CSR_MCYCLEH.value, 0x0L, writable = false), AlwaysUpdate(params => params("cycle")(63, 32))),
      (Register("minstreth", CSR_MINSTRETH.value, 0x0L, writable = false), AlwaysUpdate(params => params("instret")(63, 32))),
    )

    override def checkInterrupts(regs: Map[String, UInt], extra: Map[String, UInt]): (Bool, UInt, UInt) = {
      val mstatus = regs.getOrElse("mstatus", 0.U)
      val mie     = regs.getOrElse("mie", 0.U)
      val mip     = regs.getOrElse("mip", 0.U)
      val mtvec   = regs.getOrElse("mtvec", 0.U)

      val mstatus_mie         = mstatus(3)
      val pending_and_enabled = mip & mie

      val ext_irq = pending_and_enabled(11) // MEIP
      val tim_irq = pending_and_enabled(7)  // MTIP
      val sft_irq = pending_and_enabled(3)  // MSIP

      val take_irq = mstatus_mie && (ext_irq || sft_irq || tim_irq)

      val async_bit = 1.U(1.W) << 31
      val cause     = Mux(ext_irq, async_bit | 11.U, Mux(sft_irq, async_bit | 3.U, Mux(tim_irq, async_bit | 7.U, 0.U)))

      val target = mtvec

      (take_irq, target, cause)
    }

    override def getTrapUpdates(regs: Map[String, UInt], pc: UInt, cause: UInt): Map[String, UInt] = {
      val mstatus = regs.getOrElse("mstatus", 0.U)

      val mie_bit     = mstatus(3)
      val new_mstatus = Cat(mstatus(31, 8), mie_bit, mstatus(6, 4), 0.U(1.W), mstatus(2, 0))

      Map(
        "mstatus" -> new_mstatus,
        "mepc"    -> pc,
        "mcause"  -> cause
      )
    }

    override def getTrapReturnTarget(regs: Map[String, UInt]): UInt =
      regs.getOrElse("mepc", 0.U)

    override def getTrapReturnUpdates(regs: Map[String, UInt]): Map[String, UInt] = {
      val mstatus = regs.getOrElse("mstatus", 0.U)

      val mpie        = mstatus(7)
      val new_mstatus = Cat(mstatus(31, 8), 1.U(1.W), mstatus(6, 4), mpie, mstatus(2, 0))

      Map(
        "mstatus" -> new_mstatus
      )
    }
  }

  override def factory: UtilitiesFactory[CsrUtilities] = CsrUtilitiesFactory
}
