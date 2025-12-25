package arch.core.decoder

import chisel3.util.BitPat

trait RV32IDecodeConsts {
  // Utility
  def X = BitPat("b?")
  def N = BitPat("b0")
  def Y = BitPat("b1")

  // Branch
  def BR_X   = BitPat("b???")
  val SZ_BR  = BR_X.getWidth
  def BR_EQ  = BitPat("b000")
  def BR_NE  = BitPat("b001")
  def BR_J   = BitPat("b010")
  def BR_N   = BitPat("b011")
  def BR_LT  = BitPat("b100")
  def BR_GE  = BitPat("b101")
  def BR_LTU = BitPat("b110")
  def BR_GEU = BitPat("b111")

  // Immediate
  def IMM_X  = BitPat("b???")
  val SZ_IMM = IMM_X.getWidth
  def IMM_I  = BitPat("b000")
  def IMM_S  = BitPat("b001")
  def IMM_B  = BitPat("b010")
  def IMM_U  = BitPat("b011")
  def IMM_J  = BitPat("b100")

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

  // LSU
  def M_X   = BitPat("b???")
  val SZ_M  = M_X.getWidth
  def M_SB  = BitPat("b000")
  def M_SH  = BitPat("b001")
  def M_SW  = BitPat("b010")
  def M_LB  = BitPat("b011")
  def M_LH  = BitPat("b100")
  def M_LW  = BitPat("b101")
  def M_LBU = BitPat("b110")
  def M_LHU = BitPat("b111")
}
