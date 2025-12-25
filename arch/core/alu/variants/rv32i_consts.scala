package arch.core.alu

import chisel3.util.BitPat

trait RV32IALUConsts {
  // ALU
  def A1_X    = BitPat("b??")
  val SZ_A1   = A1_X.getWidth
  def A1_ZERO = BitPat("b00")
  def A1_RS1  = BitPat("b01")
  def A1_PC   = BitPat("b10")

  def A2_X    = BitPat("b??")
  val SZ_A2   = A2_X.getWidth
  def A2_ZERO = BitPat("b00")
  def A2_RS2  = BitPat("b01")
  def A2_IMM  = BitPat("b10")
  def A2_FOUR = BitPat("b11")

  def AFN_X    = BitPat("b???")
  val SZ_AFN   = AFN_X.getWidth
  def AFN_ADD  = BitPat("b000")
  def AFN_SLL  = BitPat("b001")
  def AFN_SLT  = BitPat("b010")
  def AFN_SLTU = BitPat("b011")
  def AFN_XOR  = BitPat("b100")
  def AFN_SRL  = BitPat("b101")
  def AFN_OR   = BitPat("b110")
  def AFN_AND  = BitPat("b111")
}
