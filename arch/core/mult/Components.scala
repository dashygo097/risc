package arch.core.mult

import arch.configs._
import chisel3._
import vopts.math.Multiplier

class MultIO(implicit p: Parameters) extends Bundle {
  val en   = Input(Bool())
  val kill = Input(Bool())

  val src1     = Input(UInt(p(XLen).W))
  val src2     = Input(UInt(p(XLen).W))
  val a_signed = Input(Bool())
  val b_signed = Input(Bool())
  val high     = Input(Bool())

  val result = Output(UInt(p(XLen).W))
  val busy   = Output(Bool())
  val done   = Output(Bool())
}

trait MultUtilities extends Utilities {
  def build: Multiplier
}

object MultUtilitiesFactory extends UtilitiesFactory[MultUtilities]("Mul")

object MultInit {
  val rv32iUtils  = RV32IMultUtilities
  val rv32imUtils = RV32IMMultUtilities
}
