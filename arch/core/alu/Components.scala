package arch.core.alu

import arch.core.common.Consts
import arch.configs._
import chisel3._
import chisel3.util.BitPat

trait AluConsts extends Consts {
  def A1_X    = BitPat("b??")
  def SZ_A1   = A1_X.getWidth
  def A1_ZERO = BitPat("b00")
  def A1_RS1  = BitPat("b01")
  def A1_PC   = BitPat("b10")

  def A2_X      = BitPat("b??")
  def SZ_A2     = A2_X.getWidth
  def A2_ZERO   = BitPat("b00")
  def A2_RS2    = BitPat("b01")
  def A2_IMM    = BitPat("b10")
  def A2_PCSTEP = BitPat("b11")
}

trait AluUtilities extends Utilities {
  def sel1Width: Int
  def sel2Width: Int
  def fnTypeWidth: Int

  def fn(src1: UInt, src2: UInt, fnType: UInt, mode: Bool): UInt
}

object AluUtilitiesFactory extends UtilitiesFactory[AluUtilities]("ALU")

object AluInit {
  val rv32iUtils = RV32IAluUtilities
}
