package arch.core.bru

import arch.core.common.Consts
import chisel3._
import chisel3.util._

trait RV32IBranchConsts extends Consts {
  def BR_X    = BitPat("b???")
  val SZ_BR   = BR_X.getWidth
  def BR_EQ   = BitPat("b000")
  def BR_NE   = BitPat("b001")
  def BR_LT   = BitPat("b010")
  def BR_GE   = BitPat("b011")
  def BR_LTU  = BitPat("b100")
  def BR_GEU  = BitPat("b101")
  def BR_JAL  = BitPat("b110")
  def BR_JALR = BitPat("b111")
}

class RV32BruUtilitiesImpl extends BruUtilities with RV32IBranchConsts {
  def branchTypeWidth: Int                           = SZ_BR
  def isJump(fnType: UInt): Bool                     = fnType(2, 1) === "b11".U
  def fn(src1: UInt, src2: UInt, fnType: UInt): Bool = {
    val eq  = src1 === src2
    val lt  = src1.asSInt < src2.asSInt
    val ltu = src1 < src2

    MuxCase(
      false.B,
      Seq(
        (fnType === BR_EQ)   -> eq,
        (fnType === BR_NE)   -> !eq,
        (fnType === BR_LT)   -> lt,
        (fnType === BR_GE)   -> !lt,
        (fnType === BR_LTU)  -> ltu,
        (fnType === BR_GEU)  -> !ltu,
        (fnType === BR_JAL)  -> true.B,
        (fnType === BR_JALR) -> true.B
      )
    )
  }
}

object RV32BruUtilities extends RegisteredBruUtilities with RV32IBranchConsts {
  override def isaName: String     = "rv32i"
  override def utils: BruUtilities = new RV32BruUtilitiesImpl
}
