package arch.core.imm

import chisel3._
import chisel3.util._

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
