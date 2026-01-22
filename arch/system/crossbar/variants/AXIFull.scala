package arch.system.crossbar

import arch.configs._
import vopts.com.amba._
import chisel3._

object AXIFullCrossbarUtilities extends RegisteredUtilities[BusCrossbarUtilities] {
  override def utils: BusCrossbarUtilities = new BusCrossbarUtilities {
    override def name: String = "axif"

    override def masterType: Bundle            =
      Flipped(new AXIFullMasterExtIO(addrWidth = p(XLen), dataWidth = p(XLen), idWidth = 4))
    override def slaveType: Bundle             =
      Flipped(new AXIFullSlaveExtIO(addrWidth = p(XLen), dataWidth = p(XLen), idWidth = 4))
    override def addressMap: Seq[(Long, Long)] = p(BusAddressMap)

    override def createInterface(ibus: Bundle, dbus: Bundle): Vec[Bundle] = {
      val crossbar  = Module(new AXIFullCrossbar(p(XLen), p(XLen), 4, 2, addressMap, p(FifoDepthPerClient)))
      val interface = Wire(Vec(addressMap.length, slaveType.cloneType))

      crossbar.masters_ext(0) <> ibus
      crossbar.masters_ext(1) <> dbus

      for (i <- interface.indices)
        interface(i) <> crossbar.slaves_ext(i)

      interface
    }
    override def connectMaster(ext: Bundle, intf: Bundle): Unit           =
      ext.asInstanceOf[AXIFullMasterExtIO].connect(intf.asInstanceOf[AXIFullMasterIO])
  }

  override def factory: UtilitiesFactory[BusCrossbarUtilities] = BusCrossbarUtilitiesFactory
}
