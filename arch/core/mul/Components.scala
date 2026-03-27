package arch.core.mul

import arch.configs._
import chisel3._
import vopts.math.Multiplier

class MulIO(implicit p: Parameters) extends Bundle {
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

trait MulUtilities extends Utilities {
  def build: Multiplier
}

object MulUtilitiesFactory extends UtilitiesFactory[MulUtilities]("Mul")

object MulInit {
  val rv32iUtils  = RV32IMulUtilities
  val rv32imUtils = RV32IMMulUtilities
}
