package arch.core.regfile

import arch.core.common.Consts
import utils.Register
import chisel3._
import chisel3.util.BitPat

trait RV32IRegfileConsts extends Consts {
  val GPR_X    = BitPat("b?????")
  val SZ_GPR   = GPR_X.getWidth
  val GPR_ZERO = BitPat("b00000")
  val GPR_RA   = BitPat("b00001")
  val GPR_SP   = BitPat("b00010")
  val GPR_GP   = BitPat("b00011")
  val GPR_TP   = BitPat("b00100")
  val GPR_T0   = BitPat("b00101")
  val GPR_T1   = BitPat("b00110")
  val GPR_T2   = BitPat("b00111")
  val GPR_S0   = BitPat("b01000")
  val GPR_S1   = BitPat("b01001")
  val GPR_A0   = BitPat("b01010")
  val GPR_A1   = BitPat("b01011")
  val GPR_A2   = BitPat("b01100")
  val GPR_A3   = BitPat("b01101")
  val GPR_A4   = BitPat("b01110")
  val GPR_A5   = BitPat("b01111")
  val GPR_A6   = BitPat("b10000")
  val GPR_A7   = BitPat("b10001")
  val GPR_S2   = BitPat("b10010")
  val GPR_S3   = BitPat("b10011")
  val GPR_S4   = BitPat("b10100")
  val GPR_S5   = BitPat("b10101")
  val GPR_S6   = BitPat("b10110")
  val GPR_S7   = BitPat("b10111")
  val GPR_S8   = BitPat("b11000")
  val GPR_S9   = BitPat("b11001")
  val GPR_S10  = BitPat("b11010")
  val GPR_S11  = BitPat("b11011")
  val GPR_T3   = BitPat("b11100")
  val GPR_T4   = BitPat("b11101")
  val GPR_T5   = BitPat("b11110")
  val GPR_T6   = BitPat("b11111")
}

class RV32IRegfileUtilitiesImpl extends RegfileUtilities with RV32IRegfileConsts {
  def width: Int                = SZ_GPR
  def getRs1(instr: UInt): UInt = instr(19, 15)
  def getRs2(instr: UInt): UInt = instr(24, 20)
  def getRd(instr: UInt): UInt  = instr(11, 7)

  def extraInfo: Seq[Register] = {
    val info = Seq(
      Register("zero", 0x0, 0x0L, writable = false, readable = true),
    )
    for (i <- 1 until 32) {
      val name = i match {
        case 1  => "ra"
        case 2  => "sp"
        case 3  => "gp"
        case 4  => "tp"
        case 5  => "t0"
        case 6  => "t1"
        case 7  => "t2"
        case 8  => "s0"
        case 9  => "s1"
        case 10 => "a0"
        case 11 => "a1"
        case 12 => "a2"
        case 13 => "a3"
        case 14 => "a4"
        case 15 => "a5"
        case 16 => "a6"
        case 17 => "a7"
        case 18 => "s2"
        case 19 => "s3"
        case 20 => "s4"
        case 21 => "s5"
        case 22 => "s6"
        case 23 => "s7"
        case 24 => "s8"
        case 25 => "s9"
        case 26 => "s10"
        case 27 => "s11"
        case 28 => "t3"
        case 29 => "t4"
        case 30 => "t5"
        case 31 => "t6"
      }
      info ++ Seq(Register(name, i, 0x0L, writable = true, readable = true))
    }

    info
  }
}

object RV32IRegfileUtilities extends RegisteredRegfileUtilities {
  override def isaName: String         = "rv32i"
  override def utils: RegfileUtilities = new RV32IRegfileUtilitiesImpl
}
