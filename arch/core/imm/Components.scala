package arch.core.imm

import arch.configs._
import chisel3._

trait ImmUtilities extends Utilities {
  def immTypeWidth: Int
  def genImm(instr: UInt, immType: UInt): UInt
  def genCsrImm(instr: UInt): UInt
}

object ImmUtilitiesFactory extends UtilitiesFactory[ImmUtilities]("Imm")

object ImmInit {
  val rv32iUtils = RV32IImmUtilities
}
