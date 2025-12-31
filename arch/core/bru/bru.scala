package arch.core.bru

import arch.configs._
import chisel3._

// TODO: JAL/JALR handling
class Bru(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_bru"

  val utils = BruUtilitiesFactory.getOrThrow(p(ISA))

  val en     = IO(Input(Bool()))
  val src1   = IO(Input(UInt(p(XLen).W)))
  val src2   = IO(Input(UInt(p(XLen).W)))
  val imm    = IO(Input(UInt(p(XLen).W)))
  val brType = IO(Input(UInt(utils.branchTypeWidth.W)))
  val target = IO(Output(UInt(p(XLen).W)))
  val cmp    = IO(Output(Bool()))

  cmp    := en && utils.fn(src1, src2, brType)
  target := src1 + imm
}
