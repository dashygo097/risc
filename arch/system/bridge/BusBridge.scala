package arch.system.bridge

import arch.configs._
import vopts.mem.cache._
import chisel3._

class BusBridge(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(BusType)}_bridge"

  val utils = BusBridgeUtilitiesFactory.getOrThrow(p(BusType))

  val imem = IO(Flipped(new CacheReadOnlyIO(Vec(p(L1ICacheLineSize) / (p(XLen) / 8), UInt(p(ILen).W)), p(XLen))))
  val dmem = IO(Flipped(new CacheIO(Vec(p(L1DCacheLineSize) / (p(XLen) / 8), UInt(p(XLen).W)), p(XLen))))

  val ibus = IO(utils.busType)
  val dbus = IO(utils.busType)

  dontTouch(imem)
  dontTouch(dmem)
  dontTouch(ibus)
  dontTouch(dbus)

  ibus <> utils.createBridgeReadOnly(Vec(p(L1ICacheLineSize) / (p(XLen) / 8), UInt(p(ILen).W)), imem)
  dbus <> utils.createBridge(Vec(p(L1DCacheLineSize) / (p(XLen) / 8), UInt(p(XLen).W)), dmem)
}
