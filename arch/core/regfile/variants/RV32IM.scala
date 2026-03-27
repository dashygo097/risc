package arch.core.regfile

import arch.configs._
import vopts.utils.Register
import chisel3._

object RV32IMRegfileUtilities extends RegisteredUtilities[RegfileUtilities] {
  override def utils: RegfileUtilities = new RegfileUtilities {
    override def name: String              = "rv32im"
    override def getRs1(instr: UInt): UInt = RV32IRegfileUtilities.utils.getRs1(instr)
    override def getRs2(instr: UInt): UInt = RV32IRegfileUtilities.utils.getRs2(instr)
    override def getRd(instr: UInt): UInt  = RV32IRegfileUtilities.utils.getRd(instr)
    override def extraInfo: Seq[Register]  = RV32IRegfileUtilities.utils.extraInfo
  }

  override def factory: UtilitiesFactory[RegfileUtilities] = RegfileUtilitiesFactory
}
