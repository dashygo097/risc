package arch.core.regfile

import arch.configs._
import vopts.utils.Register
import chisel3._

trait RegfileUtils extends Utils {
  def getRs1(instr: UInt): UInt
  def getRs2(instr: UInt): UInt
  def getRd(instr: UInt): UInt
  def extraInfo: Seq[Register]

  def writable(addr: UInt): Bool
  def readable(addr: UInt): Bool
}

object RegfileUtilsFactory extends UtilsFactory[RegfileUtils]("Regfile")

object RegfileInit {
  val rv32iUtils  = riscv.RV32IRegfileUtils
  val rv32imUtils = riscv.RV32IMRegfileUtils
}
