package arch.core.alu

import arch.configs._
import chisel3._

object RV32IMAluUtilities extends RegisteredUtilities[AluUtilities] {
  override def utils: AluUtilities = new AluUtilities {
    override def name: String                                               = "rv32im"
    override def sel1Width: Int                                             = RV32IAluUtilities.utils.sel1Width
    override def sel2Width: Int                                             = RV32IAluUtilities.utils.sel2Width
    override def fnTypeWidth: Int                                           = RV32IAluUtilities.utils.fnTypeWidth
    override def decode(uop: UInt): AluCtrl                                 = RV32IAluUtilities.utils.decode(uop)
    override def fn(src1: UInt, src2: UInt, fnType: UInt, mode: Bool): UInt = RV32IAluUtilities.utils.fn(src1, src2, fnType, mode)
  }

  override def factory: UtilitiesFactory[AluUtilities] = AluUtilitiesFactory
}
