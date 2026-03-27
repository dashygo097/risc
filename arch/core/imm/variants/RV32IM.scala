package arch.core.imm

import arch.configs._
import chisel3._

object RV32IMImmUtilities extends RegisteredUtilities[ImmUtilities] {
  override def utils: ImmUtilities = new ImmUtilities {
    override def name: String                             = "rv32im"
    override def immTypeWidth: Int                        = RV32IImmUtilities.utils.immTypeWidth
    override def genImm(instr: UInt, immType: UInt): UInt = RV32IImmUtilities.utils.genImm(instr, immType)
    override def genCsrImm(instr: UInt): UInt             = RV32IImmUtilities.utils.genCsrImm(instr)
  }

  override def factory: UtilitiesFactory[ImmUtilities] = ImmUtilitiesFactory
}
