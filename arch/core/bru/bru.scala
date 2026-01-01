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
  val jump   = IO(Output(Bool()))
  val target = IO(Output(UInt(p(XLen).W)))

  jump  := en && utils.isJump(brType)
  taken := en && utils.fn(src1, src2, brType)

  if (utils.hasJalr) {
    val is_jalr     = utils.isJalr(brType)
    val base        = Mux(is_jalr, src1, pc)
    val raw_target  = base + imm
    val jalr_target = raw_target & ~1.U(p(XLen).W)
    target := Mux(is_jalr, jalr_target, raw_target)
  } else {
    target := pc + imm
  }
}
