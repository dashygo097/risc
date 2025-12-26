package arch.core.alu

import arch.configs._
import chisel3._

class ALU(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_alu"

  val utils = ALUUtilitiesFactory.get(p(ISA)) match {
    case Some(u) => u
    case None    => throw new Exception(s"ALU utilities for ISA ${p(ISA)} not found!")
  }

  val en     = IO(Input(Bool()))
  val src1   = IO(Input(UInt(p(XLen).W)))
  val src2   = IO(Input(UInt(p(XLen).W)))
  val fnType = IO(Input(UInt(utils.fnTypeWidth.W)))
  val mode   = IO(Input(Bool()))
  val result = IO(Output(UInt(p(XLen).W)))

  result := Mux(en, utils.fn(src1, src2, fnType, mode), 0.U(p(XLen).W))
}
