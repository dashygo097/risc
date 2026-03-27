package arch.core.bru

import arch.configs._
import chisel3._

object RV32IMBruUtilities extends RegisteredUtilities[BruUtilities] {
  override def utils: BruUtilities                     = new BruUtilities {
    override def name: String                                   = "rv32im"
    override def branchTypeWidth: Int                           = RV32IBruUtilities.utils.branchTypeWidth
    override def hasJump: Boolean                               = RV32IBruUtilities.utils.hasJump
    override def hasJalr: Boolean                               = RV32IBruUtilities.utils.hasJalr
    override def isJalr(brType: UInt): Bool                     = RV32IBruUtilities.utils.isJalr(brType)
    override def isJump(brType: UInt): Bool                     = RV32IBruUtilities.utils.isJump(brType)
    override def fn(src1: UInt, src2: UInt, brType: UInt): Bool = RV32IBruUtilities.utils.fn(src1, src2, brType)
  }
  override def factory: UtilitiesFactory[BruUtilities] = BruUtilitiesFactory
}
