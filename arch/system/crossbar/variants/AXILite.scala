package arch.system.crossbar

import arch.configs._
import vopts.com.amba._
import chisel3._

object AXILiteCrossbarUtils extends RegisteredUtils[BusCrossbarUtils] {
  override def utils: BusCrossbarUtils = new BusCrossbarUtils {
    override def name: String = "axil"

    override def masterType: Bundle            =
      Flipped(new AXILiteMasterExtIO(addrWidth = p(XLen), dataWidth = p(XLen)))
    override def slaveType: Bundle             =
      Flipped(new AXILiteSlaveExtIO(addrWidth = p(XLen), dataWidth = p(XLen)))
    override def addressMap: Seq[(Long, Long)] = p(BusAddressMap).map { case desc => (desc.base, desc.base + desc.size) }

    override def createInterface(ibus: Bundle, dbus: Bundle, mbus: Bundle): Vec[Bundle] = {
      val crossbar  = Module(new AXILiteCrossbar(p(XLen), p(XLen), 3, addressMap, p(BusCrossbarFifoDepthPerClient)))
      val interface = Wire(Vec(addressMap.length, slaveType.cloneType))

      crossbar.masters_ext(0) <> ibus
      crossbar.masters_ext(1) <> dbus
      crossbar.masters_ext(2) <> mbus

      for (i <- interface.indices)
        interface(i) <> crossbar.slaves_ext(i)

      interface
    }
    override def connect(ext: Bundle, intf: Bundle): Unit                               =
      ext.asInstanceOf[AXILiteMasterExtIO].connect(intf.asInstanceOf[AXILiteMasterIO])
  }

  override def factory: UtilsFactory[BusCrossbarUtils] = BusCrossbarUtilsFactory
}
