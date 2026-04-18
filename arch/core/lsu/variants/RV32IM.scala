package arch.core.lsu.riscv

import arch.core.lsu._
import arch.configs._
import chisel3._

object RV32IMLsuUtils extends RegisteredUtils[LsuUtils] with RV32ILsuUOpConsts {
  override def utils: LsuUtils = new LsuUtils {
    override def name: String = "rv32im"

    override def decode(uop: UInt): LsuCtrl = RV32ILsuUtils.utils.decode(uop)
  }

  override def factory: UtilsFactory[LsuUtils] = LsuUtilsFactory
}
