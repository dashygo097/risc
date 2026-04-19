package arch.core.div

import arch.configs._
import chisel3._

class DivCtrl extends Bundle {
  val is_signed = Bool()
  val is_rem    = Bool()
}

class Div(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_divider"

  val utils = DivUtilsFactory.getOrThrow(p(ISA).name)

  val en = IO(Input(Bool()))
  val kill = IO(Input(Bool()))

  val src1 = IO(Input(UInt(p(XLen).W)))
  val src2 = IO(Input(UInt(p(XLen).W)))
  val is_signed = IO(Input(Bool()))
  val is_rem = IO(Input(Bool()))

  val result = IO(Output(UInt(p(XLen).W)))
  val busy = IO(Output(Bool()))
  val done = IO(Output(Bool()))

  val ctrls = utils.fn(en, kill, src1, src2, is_signed, is_rem)

  result := ctrls._1
  busy := ctrls._2
  done := ctrls._3
}
