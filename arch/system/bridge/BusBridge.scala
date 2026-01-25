package arch.system.bridge

import arch.configs._
import vopts.mem.cache._
import chisel3._

class BusBridge(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(BusType)}_bridge"

  val utils = BusBridgeUtilitiesFactory.getOrThrow(p(BusType))

  val imem = IO(Flipped(new UnifiedMemoryReadOnlyIO(p(ILen), p(XLen), p(L1ICacheLineSize) / (p(XLen) / 8))))
  val dmem = IO(Flipped(new UnifiedMemoryIO(p(XLen), p(XLen), p(L1DCacheLineSize) / (p(XLen) / 8), p(L1DCacheLineSize) / (p(XLen) / 8))))

  val ibus = IO(utils.busType)
  val dbus = IO(utils.busType)

  dontTouch(imem)
  dontTouch(dmem)
  dontTouch(ibus)
  dontTouch(dbus)

  ibus <> utils.createBridgeReadOnly(imem)
  dbus <> utils.createBridge(dmem)
}
