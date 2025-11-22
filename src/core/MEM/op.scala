package core.mem

import chisel3._

object MemOp extends ChiselEnum {
  val LB  = 0.U(3.W)
  val LH  = 1.U(3.W)
  val LW  = 2.U(3.W)
  val LBU = 3.U(3.W)
  val LHU = 4.U(3.W)
  val SB  = 5.U(3.W)
  val SH  = 6.U(3.W)
  val SW  = 7.U(3.W)
}
