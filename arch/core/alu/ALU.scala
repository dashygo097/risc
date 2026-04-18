package arch.core.alu

import arch.configs._
import chisel3._

class AluCtrl(fnWidth: Int) extends Bundle with AluConsts {
  val sel1 = UInt(SZ_A1.W)
  val sel2 = UInt(SZ_A2.W)
  val mode = Bool()
  val fn   = UInt(fnWidth.W)
}

class Alu(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_alu"

  val utils = AluUtilitiesFactory.getOrThrow(p(ISA).name)

  val en     = IO(Input(Bool()))
  val src1   = IO(Input(UInt(p(XLen).W)))
  val src2   = IO(Input(UInt(p(XLen).W)))
  val fn     = IO(Input(UInt(utils.fnTypeWidth.W)))
  val mode   = IO(Input(Bool()))
  val result = IO(Output(UInt(p(XLen).W)))

  result := Mux(en, utils.fn(src1, src2, fn, mode), 0.U(p(XLen).W))
}
