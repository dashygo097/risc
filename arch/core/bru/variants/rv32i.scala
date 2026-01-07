package arch.core.bru

import arch.core.common.Consts
import arch.configs._
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

object RV32IBruUtilities extends RegisteredUtilities[BruUtilities] with RV32IBranchConsts {
  override def utils: BruUtilities = new BruUtilities {
    override def name: String = "rv32i"

    override def branchTypeWidth: Int       = SZ_BR
    override def hasJump: Boolean           = true
    override def hasJalr: Boolean           = true
    override def isJalr(brType: UInt): Bool = brType === BR_JALR
    override def isJump(brType: UInt): Bool = brType(2, 1) === "b11".U

    override def fn(src1: UInt, src2: UInt, brType: UInt): Bool = {
      val eq  = src1 === src2
      val lt  = src1.asSInt < src2.asSInt
      val ltu = src1 < src2

      MuxCase(
        false.B,
        Seq(
          (brType === BR_EQ.value.U(SZ_BR.W))   -> eq,
          (brType === BR_NE.value.U(SZ_BR.W))   -> !eq,
          (brType === BR_LT.value.U(SZ_BR.W))   -> lt,
          (brType === BR_GE.value.U(SZ_BR.W))   -> !lt,
          (brType === BR_LTU.value.U(SZ_BR.W))  -> ltu,
          (brType === BR_GEU.value.U(SZ_BR.W))  -> !ltu,
          (brType === BR_JAL.value.U(SZ_BR.W))  -> true.B,
          (brType === BR_JALR.value.U(SZ_BR.W)) -> true.B
        )
      )
    }
  }

  override def factory: UtilitiesFactory[BruUtilities] = BruUtilitiesFactory
}
