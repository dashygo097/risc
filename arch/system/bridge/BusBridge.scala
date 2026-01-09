package arch.system.bridge

import arch.configs._
import vopts.mem.cache._
import chisel3._

class BusBridge(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(BusType)}_bridge"

  val utils = BusBridgeUtilitiesFactory.getOrThrow(p(BusType))

  // TODO: Intergrate cache for different memory io type
  val imem = IO(Flipped(new UnifiedMemoryIO(p(XLen), p(XLen), 1, 1)))
  val dmem = IO(Flipped(new UnifiedMemoryIO(p(XLen), p(XLen), 1, 1)))

  val ibus = IO(utils.busType)
  val dbus = IO(utils.busType)

  ibus <> utils.createBridge(imem)
  dbus <> utils.createBridge(dmem)
}
