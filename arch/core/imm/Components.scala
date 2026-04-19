package arch.core.imm

import arch.configs._
import chisel3._

trait ImmUtils extends Utils {
  def immTypeWidth: Int
  def genImm(instr: UInt, immType: UInt): UInt
  def genCsrImm(instr: UInt): UInt
}

object ImmUtilsFactory extends UtilsFactory[ImmUtils]("Imm")

object ImmInit {
  val rv32iUtils  = riscv.RV32IImmUtils
  val rv32imUtils = riscv.RV32IMImmUtils
}
