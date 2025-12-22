package core.common

import chisel3._

object ForwardingStage extends ChiselEnum {
  val NONE = "b00".U
  val EX   = "b01".U
  val MEM  = "b10".U
  val WB   = "b11".U
}
