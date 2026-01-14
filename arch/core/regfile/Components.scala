package arch.core.regfile

import arch.configs._
import vopts.utils.Register
import chisel3._

trait RegfileUtilities extends Utilities {
  def getRs1(instr: UInt): UInt
  def getRs2(instr: UInt): UInt
  def getRd(instr: UInt): UInt
  def extraInfo: Seq[Register]
}

object RegfileUtilitiesFactory extends UtilitiesFactory[RegfileUtilities]("Regfile")

object RegfileInit {
  val rv32iUtils = RV32IRegfileUtilities
}
