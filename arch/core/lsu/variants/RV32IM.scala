package arch.core.lsu

import arch.configs._
import chisel3._

object RV32IMLsuUtilities extends RegisteredUtilities[LsuUtilities] with RV32ILsuUOpConsts {
  override def utils: LsuUtilities = new LsuUtilities {
    override def name: String = "rv32im"

    override def decodeUop(uop: UInt): LsuCtrl = RV32ILsuUtilities.utils.decodeUop(uop)
  }

  override def factory: UtilitiesFactory[LsuUtilities] = LsuUtilitiesFactory
}
