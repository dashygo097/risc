package arch.core.bru

import arch.configs._
import chisel3._

class BruCtrl(val opWidth: Int) extends Bundle {
  val is_jump = Bool()
  val is_jalr = Bool()
  val op      = UInt(opWidth.W)
}

class Bru(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_bru"

  val utils = BruUtilitiesFactory.getOrThrow(p(ISA).name)

  val en   = IO(Input(Bool()))
  val pc   = IO(Input(UInt(p(XLen).W)))
  val src1 = IO(Input(UInt(p(XLen).W)))
  val src2 = IO(Input(UInt(p(XLen).W)))
  val imm  = IO(Input(UInt(p(XLen).W)))
  val uop  = IO(Input(UInt(p(MicroOpWidth).W)))

  val taken  = IO(Output(Bool()))
  val jump   = IO(Output(Bool()))
  val target = IO(Output(UInt(p(XLen).W)))

  val ctrl = utils.decodeUop(uop)

  jump  := en && ctrl.is_jump
  taken := en && utils.fn(src1, src2, ctrl.op)

  if (utils.hasJalr) {
    val base: UInt        = Mux(ctrl.is_jalr, src1, pc)
    val raw_target: UInt  = base + imm
    val jalr_target: UInt = raw_target & ~1.U(p(XLen).W)
    target := Mux(ctrl.is_jalr, jalr_target, raw_target)
  } else {
    target := pc + imm
  }
}
