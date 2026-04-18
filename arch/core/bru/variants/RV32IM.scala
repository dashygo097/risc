package arch.core.bru

import arch.configs._
import chisel3._

object RV32IMBruUtilities extends RegisteredUtilities[BruUtilities] {
  override def utils: BruUtilities                     = new BruUtilities {
    override def name: String                               = "rv32im"
    override def opWidth: Int                               = RV32IBruUtilities.utils.opWidth
    override def hasJump: Boolean                           = RV32IBruUtilities.utils.hasJump
    override def hasJalr: Boolean                           = RV32IBruUtilities.utils.hasJalr
    override def decode(uop: UInt): BruCtrl                 = RV32IBruUtilities.utils.decode(uop)
    override def fn(src1: UInt, src2: UInt, op: UInt): Bool = RV32IBruUtilities.utils.fn(src1, src2, op)
  }
  override def factory: UtilitiesFactory[BruUtilities] = BruUtilitiesFactory
}
