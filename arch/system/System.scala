package arch.system

import bridge._
import crossbar._
import arch.configs._
import arch.core._
import arch.core.csr._
import chisel3._
import chisel3.util.log2Ceil

class RiscSystem(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_system"

  val bridge_utils   = BusBridgeUtilitiesFactory.getOrThrow(p(BusType))
  val crossbar_utils = BusCrossbarUtilitiesFactory.getOrThrow(p(BusType))

  val devices = IO(Vec(p(BusAddressMap).length, crossbar_utils.slaveType)).suggestName(s"M_${p(BusType)}".toUpperCase)
  val irq     = IO(new CoreInterruptIO)

  dontTouch(devices)

  // Modules
  val cpu      = Module(new RiscCore)
  val bridge   = Module(new BusBridge)
  val crossbar = Module(new BusCrossbar)

  cpu.imem <> bridge.imem
  cpu.dmem <> bridge.dmem
  cpu.mmio <> bridge.mmio
  cpu.irq <> irq

  crossbar_utils.connect(crossbar.ibus, bridge.ibus)
  crossbar_utils.connect(crossbar.dbus, bridge.dbus)
  crossbar_utils.connect(crossbar.mbus, bridge.mbus)

  for (i <- devices.indices)
    devices(i) <> crossbar.devices(i)

  // Debug
  val debug_cycle_count   = IO(Output(UInt(64.W)))
  val debug_instret_count = IO(Output(UInt(64.W)))
  val debug_instret       = IO(Output(Bool()))
  val debug_pc            = IO(Output(UInt(p(XLen).W)))
  val debug_instr         = IO(Output(UInt(p(ILen).W)))
  val debug_reg_we        = IO(Output(Bool()))
  val debug_reg_addr      = IO(Output(UInt(log2Ceil(p(NumArchRegs)).W)))
  val debug_reg_data      = IO(Output(UInt(p(XLen).W)))

  val debug_branch_taken  = IO(Output(Bool()))
  val debug_branch_source = IO(Output(UInt(p(XLen).W)))
  val debug_branch_target = IO(Output(UInt(p(XLen).W)))

  val debug_if_instr  = IO(Output(UInt(p(ILen).W)))
  val debug_id_instr  = IO(Output(UInt(p(ILen).W)))
  val debug_ex_instr  = IO(Output(UInt(p(ILen).W)))
  val debug_mem_instr = IO(Output(UInt(p(ILen).W)))
  val debug_wb_instr  = IO(Output(UInt(p(ILen).W)))

  val debug_l1_icache_access = IO(Output(Bool()))
  val debug_l1_icache_miss   = IO(Output(Bool()))
  val debug_l1_dcache_access = IO(Output(Bool()))
  val debug_l1_dcache_miss   = IO(Output(Bool()))

  debug_cycle_count   := cpu.debug_cycle_count
  debug_instret_count := cpu.debug_instret_count
  debug_instret       := cpu.debug_instret
  debug_pc            := cpu.debug_pc
  debug_instr         := cpu.debug_instr
  debug_reg_we        := cpu.debug_reg_we
  debug_reg_addr      := cpu.debug_reg_addr
  debug_reg_data      := cpu.debug_reg_data

  debug_branch_taken  := cpu.debug_branch_taken
  debug_branch_source := cpu.debug_branch_source
  debug_branch_target := cpu.debug_branch_target

  debug_if_instr  := cpu.debug_if_instr
  debug_id_instr  := cpu.debug_id_instr
  debug_ex_instr  := cpu.debug_ex_instr
  debug_mem_instr := cpu.debug_mem_instr
  debug_wb_instr  := cpu.debug_wb_instr

  debug_l1_icache_access := cpu.debug_l1_icache_access
  debug_l1_icache_miss   := cpu.debug_l1_icache_miss
  debug_l1_dcache_access := cpu.debug_l1_dcache_access
  debug_l1_dcache_miss   := cpu.debug_l1_dcache_miss
}
