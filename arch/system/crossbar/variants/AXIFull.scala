package arch.system.crossbar

import arch.configs._
import vopts.com.amba._
import chisel3._

object AXIFullCrossbarUtils extends RegisteredUtils[BusCrossbarUtils] {
  override def utils: BusCrossbarUtils = new BusCrossbarUtils {
    override def name: String = "axif"

    override def masterType: Bundle            =
      Flipped(new AXIFullMasterExtIO(addrWidth = p(XLen), dataWidth = p(XLen), idWidth = 4))
    override def slaveType: Bundle             =
      Flipped(new AXIFullSlaveExtIO(addrWidth = p(XLen), dataWidth = p(XLen), idWidth = 4))
    override def addressMap: Seq[(Long, Long)] = p(BusAddressMap).map { case desc => (desc.base, desc.base + desc.size) } // (base, size) -> (begin, end)

    override def createInterface(ibus: Bundle, dbus: Bundle, mbus: Bundle): Vec[Bundle] = {
      val crossbar  = Module(new AXIFullCrossbar(p(XLen), p(XLen), 4, 3, addressMap, p(BusCrossbarFifoDepthPerClient)))
      val interface = Wire(Vec(addressMap.length, slaveType.cloneType))

      crossbar.masters_ext(0) <> ibus
      crossbar.masters_ext(1) <> dbus
      crossbar.masters_ext(2) <> mbus

      for (i <- interface.indices)
        interface(i) <> crossbar.slaves_ext(i)

      interface
    }
    override def connect(ext: Bundle, intf: Bundle): Unit                               =
      ext.asInstanceOf[AXIFullMasterExtIO].connect(intf.asInstanceOf[AXIFullMasterIO])
  }

  override def factory: UtilsFactory[BusCrossbarUtils] = BusCrossbarUtilsFactory
}
