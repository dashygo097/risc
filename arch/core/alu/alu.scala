package arch.core.alu

import arch.configs._
import chisel3._

class Alu(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_alu"

  val utils = AluUtilitiesFactory.getOrThrow(p(ISA))

  val en     = IO(Input(Bool()))
  val src1   = IO(Input(UInt(p(XLen).W)))
  val src2   = IO(Input(UInt(p(XLen).W)))
  val fnType = IO(Input(UInt(utils.fnTypeWidth.W)))
  val mode   = IO(Input(Bool()))
  val result = IO(Output(UInt(p(XLen).W)))

  result := Mux(en, utils.fn(src1, src2, fnType, mode), 0.U(p(XLen).W))
}
