package arch.system.bridge

import arch.configs._
import vopts.mem.cache._
import chisel3._

class BusBridge(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(BusType)}_bridge"

  val utils = BusBridgeUtilitiesFactory.getOrThrow(p(BusType))

  val imem = IO(Flipped(new CacheReadOnlyIO(Vec(p(IssueWidth), UInt(p(ILen).W)), p(XLen))))
  val dmem = IO(Flipped(new CacheIO(UInt(p(XLen).W), p(XLen))))
  val mmio = IO(Flipped(new CacheIO(UInt(p(XLen).W), p(XLen))))

  val ibus = IO(utils.busType)
  val dbus = IO(utils.busType)
  val mbus = IO(utils.busType)

  dontTouch(imem)
  dontTouch(dmem)
  dontTouch(mmio)
  dontTouch(ibus)
  dontTouch(dbus)
  dontTouch(mbus)

  ibus <> utils.createBridgeReadOnly(Vec(p(IssueWidth), UInt(p(ILen).W)), imem, isMmio = false)
  dbus <> utils.createBridge(UInt(p(XLen).W), dmem, isMmio = false)
  mbus <> utils.createBridge(UInt(p(XLen).W), mmio, isMmio = true)
}
