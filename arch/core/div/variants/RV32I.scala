package arch.core.div

import arch.configs._
import chisel3._

object RV32IDivUtils extends RegisteredUtils[DivUtils] {
  override def utils: DivUtils = new DivUtils {
    override def name: String = "rv32i"

    override def decode(uop: UInt): DivCtrl = {
      val ctrl = Wire(new DivCtrl)
      ctrl.is_signed := false.B
      ctrl.is_rem    := false.B
      ctrl
    }

    // RV32I has no integer divide extension.
    override def fn(en: Bool, kill: Bool, src1: UInt, src2: UInt, is_signed: Bool, is_rem: Bool): (UInt, Bool, Bool) =
      (0.U(p(XLen).W), false.B, true.B)
  }

  override def factory: UtilsFactory[DivUtils] = DivUtilsFactory
}
