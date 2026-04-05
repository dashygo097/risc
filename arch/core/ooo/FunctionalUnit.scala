package arch.core.ooo

import arch.configs._
import chisel3._
import chisel3.util._

class FunctionalUnitResp(implicit p: Parameters) extends Bundle {
  val result  = UInt(p(XLen).W)
  val rd      = UInt(log2Ceil(p(NumArchRegs)).W)
  val rob_tag = UInt(log2Ceil(p(ROBSize)).W)
}

abstract class FunctionalUnit(implicit p: Parameters) extends Module {
  val req   = IO(Flipped(Decoupled(new MicroOp)))
  val resp  = IO(Valid(new FunctionalUnitResp))
  val flush = IO(Input(Bool()))
}
