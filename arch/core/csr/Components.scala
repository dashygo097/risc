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
  def addrWidth: Int
  def opWidth: Int

  def getAddr(instr: UInt): UInt
  def genImm(instr: UInt): UInt

  def decode(uop: UInt): CsrCtrl
  def fn(op: UInt, csr_data: UInt, src_data: UInt): UInt

  def table: Seq[(Register, CsrUpdateBehavior)]
  def extraInputs: Seq[(String, Int)]

  // Trap Interrupt Interface
  def checkInterrupts(regs: Map[String, UInt], extra: Map[String, UInt]): (Bool, UInt, UInt) =
    (false.B, 0.U, 0.U)
  def getTrapUpdates(regs: Map[String, UInt], pc: UInt, cause: UInt): Map[String, UInt]      =
    Map.empty[String, UInt]

  // Trap Return Interface
  def getTrapReturnTarget(regs: Map[String, UInt]): UInt               = 0.U
  def getTrapReturnUpdates(regs: Map[String, UInt]): Map[String, UInt] = Map.empty
}

object CsrUtilitiesFactory extends UtilitiesFactory[CsrUtilities]("CSR")

object CsrInit {
  val rv32iUtils  = RV32ICsrUtilities
  val rv32imUtils = RV32IMCsrUtilities
}
