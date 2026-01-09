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

  // TODO: Temporarily for testing purposes only
  val ibus = IO(bridge_utils.busType)
  val dbus = IO(bridge_utils.busType)

  // Modules
  val cpu      = Module(new RiscCore)
  val bridge   = Module(new BusBridge)
  val crossbar = Module(new BusCrossbar)

  cpu.imem <> bridge.imem
  cpu.dmem <> bridge.dmem
  bridge.dbus <> dbus
  bridge.ibus <> ibus
}
