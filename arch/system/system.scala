package arch.system

import bridge._
import arch.configs._
import arch.core._
import chisel3._

class RiscSystem(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_system"

  // Modules
  val cpu    = Module(new RiscCore)
  val bridge = Module(new BusBridge)
}

/*
 *
 *class RiscCore(implicit p: Parameters) extends Module with ForwardingConsts with AluConsts {
  override def desiredName: String = s"${p(ISA)}_cpu"

  val regfile_utils = RegfileUtilitiesFactory.getOrThrow(p(ISA))
  val alu_utils     = AluUtilitiesFactory.getOrThrow(p(ISA))
  val lsu_utils     = LsuUtilitiesFactory.getOrThrow(p(ISA))

  val imem = IO(new UnifiedMemoryIO(p(XLen), p(XLen), 1, 1))
  val dmem = IO(new UnifiedMemoryIO(p(XLen), p(XLen), 1, 1))

  // Debug
  val debug_pc       = IO(Output(UInt(p(XLen).W)))
  val debug_instr    = IO(Output(UInt(p(ILen).W)))
  val debug_reg_we   = IO(Output(Bool()))
  val debug_reg_addr = IO(Output(UInt(regfile_utils.width.W)))
  val debug_reg_data = IO(Output(UInt(p(XLen).W)))
 *
 */
