package arch.system.crossbar

import arch.configs._
import vopts.com.amba._
import chisel3._

object AXI4CrossbarUtilities extends RegisteredUtilities[BusCrossbarUtilities] {
  override def utils: BusCrossbarUtilities = new BusCrossbarUtilities {
    override def name: String = "axi"

    override def masterType: Bundle            =
      Flipped(new AXILiteMasterExtIO(addrWidth = p(XLen), dataWidth = p(XLen)))
    override def slaveType: Bundle             =
      Flipped(new AXILiteSlaveExtIO(addrWidth = p(XLen), dataWidth = p(XLen)))
    override def addressMap: Seq[(Long, Long)] = p(BusAddressMap)

    override def createInterface(ibus: Bundle, dbus: Bundle): Vec[Bundle] = {
      val crossbar  = Module(new AXILiteCrossbar(p(XLen), p(XLen), 2, addressMap, p(FifoDepthPerClient)))
      val interface = Wire(Vec(addressMap.length, slaveType.cloneType))

      crossbar.masters_ext(0) <> ibus
      crossbar.masters_ext(1) <> dbus

      for (i <- interface.indices)
        interface(i) <> crossbar.slaves_ext(i)

      interface
    }
    override def connectMaster(ext: Bundle, intf: Bundle): Unit           =
      ext.asInstanceOf[AXILiteMasterExtIO].connect(intf.asInstanceOf[AXILiteMasterIO])
  }

  override def factory: UtilitiesFactory[BusCrossbarUtilities] = BusCrossbarUtilitiesFactory
}
