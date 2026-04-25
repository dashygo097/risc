package arch.core.ooo

import arch.configs._
import chisel3._
import chisel3.util.{ log2Ceil, Decoupled }

class FunctionalUnitResp(implicit p: Parameters) extends Bundle {
  val result  = UInt(p(XLen).W)
  val rd      = UInt(log2Ceil(p(NumArchRegs)).W)
  val pc      = UInt(p(XLen).W)
  val instr   = UInt(p(ILen).W)
  val rob_tag = UInt(log2Ceil(p(ROBSize)).W)
}

class FunctionalUnitIO(implicit p: Parameters) extends Bundle {
  val req   = Flipped(Decoupled(new MicroOp))
  val resp  = Decoupled(new FunctionalUnitResp)
  val flush = Input(Bool())
}

abstract class FunctionalUnit(implicit p: Parameters) extends Module {
  val io = IO(new FunctionalUnitIO)
}
