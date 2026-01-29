package arch.core.bpu

import chisel3._
import arch.configs._

class InstructionPredictor(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_instr_predictor"

  val instr = IO(Input(UInt(p(ILen).W)))
  val taken = IO(Output(Bool()))

  // TODO: Now it is just a static predictor always not taken
  taken := false.B
}
