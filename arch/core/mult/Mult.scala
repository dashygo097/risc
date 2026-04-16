package arch.core.mult

import arch.configs._
import chisel3._

class Mult(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_multiplier"

  val utils = MultUtilitiesFactory.getOrThrow(p(ISA).name)

  val en   = IO(Input(Bool()))
  val kill = IO(Input(Bool()))

  val src1     = IO(Input(UInt(p(XLen).W)))
  val src2     = IO(Input(UInt(p(XLen).W)))
  val a_signed = IO(Input(Bool()))
  val b_signed = IO(Input(Bool()))
  val high     = IO(Input(Bool()))

  val result = IO(Output(UInt(p(XLen).W)))
  val busy   = IO(Output(Bool()))
  val done   = IO(Output(Bool()))

  val ctrls = utils.fn(en, kill, src1, src2, a_signed, b_signed, high)

  result := ctrls._1
  busy   := ctrls._2
  done   := ctrls._3
}
