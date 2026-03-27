package arch.core.csr

import arch.configs._
import vopts.utils.Register
import chisel3._

object CsrUpdateBehavior {
  type CsrUpdateFn = Map[String, UInt] => UInt
}

sealed trait CsrUpdateBehavior
case object NormalUpdate                                        extends CsrUpdateBehavior
case class AlwaysUpdate(fn: CsrUpdateBehavior.CsrUpdateFn)      extends CsrUpdateBehavior
case class ConditionalUpdate(fn: CsrUpdateBehavior.CsrUpdateFn) extends CsrUpdateBehavior

trait CsrUtilities extends Utilities {
  def cmdWidth: Int
  def addrWidth: Int
  def immWidth: Int
  def isImm(cmd: UInt): Bool
  def genImm(imm: UInt): UInt
  def getAddr(instr: UInt): UInt

  def fn(cmd: UInt, csr_data: UInt, src_data: UInt): UInt
  def table: Seq[(Register, CsrUpdateBehavior)]
  def extraInputs: Seq[(String, Int)]
}

object CsrUtilitiesFactory extends UtilitiesFactory[CsrUtilities]("CSR")

object CsrInit {
  val rv32iUtils  = RV32ICsrUtilities
  val rv32imUtils = RV32IMCsrUtilities
}
