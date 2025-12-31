package arch.core.bru

import arch.configs._
import chisel3._

class Bru(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_bru"

  val utils = BruUtilitiesFactory.getOrThrow(p(ISA))

  val en     = IO(Input(Bool()))
  val pc     = IO(Input(UInt(p(XLen).W)))
  val src1   = IO(Input(UInt(p(XLen).W)))
  val src2   = IO(Input(UInt(p(XLen).W)))
  val imm    = IO(Input(UInt(p(XLen).W)))
  val brType = IO(Input(UInt(utils.branchTypeWidth.W)))
  val taken  = IO(Output(Bool()))
  val target = IO(Output(UInt(p(XLen).W)))

  taken  := en && utils.fn(src1, src2, brType)
  target := pc + imm
}
