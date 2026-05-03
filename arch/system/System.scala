package arch.system

import bridge._
import crossbar._
import arch.configs._
import arch.core._
import arch.core.csr._
import chisel3._
import chisel3.util.log2Ceil

class RiscSystem(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_system"

  val bridge_utils   = BusBridgeUtilsFactory.getOrThrow(p(BusType))
  val crossbar_utils = BusCrossbarUtilsFactory.getOrThrow(p(BusType))

  val devices = IO(Vec(p(BusAddressMap).length, crossbar_utils.slaveType)).suggestName(s"M_${p(BusType)}".toUpperCase)
  val irq     = IO(new CoreInterruptIO)

  dontTouch(devices)

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

  val debug_cycle_count   = IO(Output(UInt(64.W)))
  val debug_instret_count = IO(Output(UInt(64.W)))

  val debug_instret  = IO(Output(Vec(p(IssueWidth), Bool())))
  val debug_pc       = IO(Output(Vec(p(IssueWidth), UInt(p(XLen).W))))
  val debug_instr    = IO(Output(Vec(p(IssueWidth), UInt(p(ILen).W))))
  val debug_reg_we   = IO(Output(Vec(p(IssueWidth), Bool())))
  val debug_reg_addr = IO(Output(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W))))
  val debug_reg_data = IO(Output(Vec(p(IssueWidth), UInt(p(XLen).W))))

  val debug_branch_taken  = IO(Output(Bool()))
  val debug_branch_source = IO(Output(UInt(p(XLen).W)))
  val debug_branch_target = IO(Output(UInt(p(XLen).W)))

  val debug_l1_icache_access = IO(Output(Bool()))
  val debug_l1_icache_miss   = IO(Output(Bool()))
  val debug_l1_dcache_access = IO(Output(Bool()))
  val debug_l1_dcache_miss   = IO(Output(Bool()))

  val debug_flush_cycle    = IO(Output(Bool()))
  val debug_bpu_mispredict = IO(Output(Bool()))
  val debug_branch_commit  = IO(Output(UInt(log2Ceil(p(IssueWidth) + 1).W)))
  val debug_rob_empty      = IO(Output(Bool()))
  val debug_issue_count    = IO(Output(UInt(log2Ceil(p(IssueWidth) + 1).W)))
  val debug_commit_count   = IO(Output(UInt(log2Ceil(p(IssueWidth) + 1).W)))

  val debug_frontend_stall = IO(Output(Bool()))
  val debug_backend_stall  = IO(Output(Bool()))

  debug_cycle_count   := cpu.debug_cycle_count
  debug_instret_count := cpu.debug_instret_count

  debug_instret  := cpu.debug_instret
  debug_pc       := cpu.debug_pc
  debug_instr    := cpu.debug_instr
  debug_reg_we   := cpu.debug_reg_we
  debug_reg_addr := cpu.debug_reg_addr
  debug_reg_data := cpu.debug_reg_data

  debug_branch_taken  := cpu.debug_branch_taken
  debug_branch_source := cpu.debug_branch_source
  debug_branch_target := cpu.debug_branch_target

  debug_l1_icache_access := cpu.debug_l1_icache_access
  debug_l1_icache_miss   := cpu.debug_l1_icache_miss
  debug_l1_dcache_access := cpu.debug_l1_dcache_access
  debug_l1_dcache_miss   := cpu.debug_l1_dcache_miss

  debug_flush_cycle    := cpu.debug_flush_cycle
  debug_bpu_mispredict := cpu.debug_bpu_mispredict
  debug_branch_commit  := cpu.debug_branch_commit
  debug_rob_empty      := cpu.debug_rob_empty
  debug_issue_count    := cpu.debug_issue_count
  debug_commit_count   := cpu.debug_commit_count

  debug_frontend_stall := cpu.debug_frontend_stall
  debug_backend_stall  := cpu.debug_backend_stall
}
