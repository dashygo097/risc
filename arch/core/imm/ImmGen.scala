package arch.core.imm

import arch.configs._
import chisel3._

class ImmGen(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_immgen"

  val utils = ImmUtilitiesFactory.getOrThrow(p(ISA))

  val instr   = IO(Input(UInt(p(ILen).W)))
  val immType = IO(Input(UInt(utils.immTypeWidth.W)))
  val imm     = IO(Output(UInt(p(XLen).W)))
  val csr_imm = IO(Output(UInt(p(XLen).W)))

  imm     := utils.genImm(instr, immType)
  csr_imm := utils.genCsrImm(instr)
}
