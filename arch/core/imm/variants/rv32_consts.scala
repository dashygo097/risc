package arch.core.imm

import arch.core.common.Consts
import chisel3.util.BitPat

trait RV32ImmConsts extends Consts {
  def IMM_X  = BitPat("b???")
  val SZ_IMM = IMM_X.getWidth
  def IMM_I  = BitPat("b000")
  def IMM_S  = BitPat("b001")
  def IMM_B  = BitPat("b010")
  def IMM_U  = BitPat("b011")
  def IMM_J  = BitPat("b100")
}
