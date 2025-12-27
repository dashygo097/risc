package arch.core.imm

import arch.core.common.Consts
import chisel3._
import chisel3.util._

trait RV32ImmConsts extends Consts {
  def IMM_X  = BitPat("b???")
  def SZ_IMM = IMM_X.getWidth
  def IMM_I  = BitPat("b000")
  def IMM_S  = BitPat("b001")
  def IMM_B  = BitPat("b010")
  def IMM_U  = BitPat("b011")
  def IMM_J  = BitPat("b100")
}

class RV32ImmUtilitiesImpl extends ImmUtilities with RV32ImmConsts {
  def immTypeWidth: Int                        = SZ_IMM
  def createBundle: UInt                       = UInt(32.W)
  def genImm(instr: UInt, immType: UInt): UInt =
    MuxLookup(immType, 0.U(SZ_IMM.W))(
      Seq(
        IMM_I.value.U(SZ_IMM.W) -> Cat(Fill(20, instr(31)), instr(31, 20)),
        IMM_S.value.U(SZ_IMM.W) -> Cat(Fill(20, instr(31)), instr(31, 25), instr(11, 7)),
        IMM_B.value.U(SZ_IMM.W) -> Cat(
          Fill(19, instr(31)),
          instr(31),
          instr(7),
          instr(30, 25),
          instr(11, 8),
          0.U(1.W)
        ),
        IMM_U.value.U(SZ_IMM.W) -> Cat(instr(31, 12), Fill(12, 0.U)),
        IMM_J.value.U(SZ_IMM.W) -> Cat(
          Fill(11, instr(31)),
          instr(31),
          instr(19, 12),
          instr(20),
          instr(30, 21),
          0.U(1.W)
        ),
      )
    )
}

object RV32ImmUtilities extends RegisteredImmUtilities with RV32ImmConsts {
  override def isaName: String     = "rv32i"
  override def utils: ImmUtilities = new RV32ImmUtilitiesImpl
}
