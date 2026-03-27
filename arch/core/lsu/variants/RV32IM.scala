package arch.core.lsu

import arch.configs._
import chisel3._

object RV32IMLsuUtilities extends RegisteredUtilities[LsuUtilities] with RV32ILsuConsts {
  override def utils: LsuUtilities = new LsuUtilities {
    override def name: String                              = "rv32im"
    override def cmdWidth: Int                             = RV32ILsuUtilities.utils.cmdWidth
    override def strb(cmd: UInt)                           = RV32ILsuUtilities.utils.strb(cmd)
    override def isByte(cmd: UInt): Bool                   = RV32ILsuUtilities.utils.isByte(cmd)
    override def isHalf(cmd: UInt): Bool                   = RV32ILsuUtilities.utils.isHalf(cmd)
    override def isWord(cmd: UInt): Bool                   = RV32ILsuUtilities.utils.isWord(cmd)
    override def isUnsigned(cmd: UInt): Bool               = RV32ILsuUtilities.utils.isUnsigned(cmd)
    override def isRead(cmd: UInt): Bool                   = RV32ILsuUtilities.utils.isRead(cmd)
    override def isWrite(cmd: UInt): Bool                  = RV32ILsuUtilities.utils.isWrite(cmd)
    override def isMemRead(is_mem: Bool, cmd: UInt): Bool  = RV32ILsuUtilities.utils.isMemRead(is_mem, cmd)
    override def isMemWrite(is_mem: Bool, cmd: UInt): Bool = RV32ILsuUtilities.utils.isMemWrite(is_mem, cmd)
  }

  override def factory: UtilitiesFactory[LsuUtilities] = LsuUtilitiesFactory
}
