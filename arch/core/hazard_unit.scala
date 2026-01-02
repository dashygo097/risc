package arch.core

import arch.configs._
import chisel3._

class HazardUnit(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_hazard_unit}"

  val imem_pending = IO(Input(Bool()))

  val if_stall  = IO(Output(Bool()))
  val id_stall  = IO(Output(Bool()))
  val ex_stall  = IO(Output(Bool()))
  val mem_stall = IO(Output(Bool()))

  when(imem_pending) {
    if_stall  := true.B
    id_stall  := false.B
    ex_stall  := false.B
    mem_stall := false.B
  }.otherwise {
    if_stall  := false.B
    id_stall  := false.B
    ex_stall  := false.B
    mem_stall := false.B
  }
}
