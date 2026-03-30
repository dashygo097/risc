package arch.core.csr

import arch.configs._
import chisel3._
import vopts.utils.Register

object RV32IMCsrUtilities extends RegisteredUtilities[CsrUtilities] {
  override def utils: CsrUtilities = new CsrUtilities {
    override def name: String                                        = "rv32im"
    override def cmdWidth: Int                                       = RV32ICsrUtilities.utils.cmdWidth
    override def addrWidth: Int                                      = RV32ICsrUtilities.utils.addrWidth
    override def immWidth: Int                                       = RV32ICsrUtilities.utils.immWidth
    override def isImm(cmd: UInt): Bool                              = RV32ICsrUtilities.utils.isImm(cmd)
    override def genImm(imm: UInt): UInt                             = RV32ICsrUtilities.utils.genImm(imm)
    override def getAddr(instr: UInt): UInt                          = RV32ICsrUtilities.utils.getAddr(instr)
    override def fn(cmd: UInt, csr_data: UInt, src_data: UInt): UInt = RV32ICsrUtilities.utils.fn(cmd, csr_data, src_data)
    override def extraInputs: Seq[(String, Int)]                     = RV32ICsrUtilities.utils.extraInputs

    override def table: Seq[(Register, CsrUpdateBehavior)] = RV32ICsrUtilities.utils.table.map {
      case (reg, behavior) if reg.name == "misa" =>
        val newMisaReg = Register(
          name = reg.name,
          addr = reg.addr,
          initValue = 0x40001100L,
          writable = reg.writable
        )
        (newMisaReg, behavior)

      case other => other
    }

    override def checkInterrupts(regs: Map[String, UInt], extra: Map[String, UInt]): (Bool, UInt, UInt) =
      RV32ICsrUtilities.utils.checkInterrupts(regs, extra)
    override def getTrapUpdates(regs: Map[String, UInt], pc: UInt, cause: UInt): Map[String, UInt]      =
      RV32ICsrUtilities.utils.getTrapUpdates(regs, pc, cause)
  }

  override def factory: UtilitiesFactory[CsrUtilities] = CsrUtilitiesFactory
}
