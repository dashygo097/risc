package arch.core.bru.riscv

import arch.core.bru._
import arch.configs._
import chisel3._

object RV32IMBruUtils extends RegisteredUtils[BruUtils] {
  override def utils: BruUtils                 = new BruUtils {
    override def name: String                               = "rv32im"
    override def opWidth: Int                               = RV32IBruUtils.utils.opWidth
    override def hasJump: Boolean                           = RV32IBruUtils.utils.hasJump
    override def hasJalr: Boolean                           = RV32IBruUtils.utils.hasJalr
    override def decode(uop: UInt): BruCtrl                 = RV32IBruUtils.utils.decode(uop)
    override def fn(src1: UInt, src2: UInt, op: UInt): Bool = RV32IBruUtils.utils.fn(src1, src2, op)
  }
  override def factory: UtilsFactory[BruUtils] = BruUtilsFactory
}
