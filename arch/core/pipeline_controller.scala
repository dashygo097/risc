package arch.core

import arch.configs._
import chisel3._

class PipelineController(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_pipeline_ctrl"

  val if_imem_pending    = IO(Input(Bool()))
  val id_load_use_hazard = IO(Input(Bool()))
  val mem_dmem_pending   = IO(Input(Bool()))

  val if_id_stall  = IO(Output(Bool()))
  val id_ex_stall  = IO(Output(Bool()))
  val ex_mem_stall = IO(Output(Bool()))
  val mem_wb_stall = IO(Output(Bool()))

  val pc_should_update = IO(Output(Bool()))

  // Default values
  if_id_stall      := false.B
  id_ex_stall      := false.B
  ex_mem_stall     := false.B
  mem_wb_stall     := false.B
  pc_should_update := true.B

  when(if_imem_pending) {
    if_id_stall      := true.B
    id_ex_stall      := true.B
    ex_mem_stall     := true.B
    mem_wb_stall     := true.B
    pc_should_update := false.B
  }.elsewhen(id_load_use_hazard) {
    if_id_stall      := true.B
    id_ex_stall      := true.B
    ex_mem_stall     := false.B
    mem_wb_stall     := false.B
    pc_should_update := false.B
  }.elsewhen(mem_dmem_pending) {
    ex_mem_stall     := true.B
    mem_wb_stall     := true.B
    pc_should_update := false.B
  }

}
