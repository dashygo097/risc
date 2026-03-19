package arch.system

import bridge._
import crossbar._
import arch.configs._
import arch.core._
import chisel3._

class RiscSystem(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_system"

  val bridge_utils   = BusBridgeUtilitiesFactory.getOrThrow(p(BusType))
  val crossbar_utils = BusCrossbarUtilitiesFactory.getOrThrow(p(BusType))

  val devices = IO(Vec(p(BusAddressMap).length, crossbar_utils.slaveType)).suggestName(s"M_${p(BusType)}".toUpperCase)

  dontTouch(devices)

  // Modules
  val cpu      = Module(new RiscCore)
  val bridge   = Module(new BusBridge)
  val crossbar = Module(new BusCrossbar)

  cpu.imem <> bridge.imem
  cpu.dmem <> bridge.dmem
  cpu.mmio <> bridge.mmio

  crossbar_utils.connect(crossbar.ibus, bridge.ibus)
  crossbar_utils.connect(crossbar.dbus, bridge.dbus)
  crossbar_utils.connect(crossbar.mbus, bridge.mbus)

  for (i <- devices.indices)
    devices(i) <> crossbar.devices(i)
}
