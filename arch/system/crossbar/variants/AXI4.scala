package arch.system.crossbar

import arch.configs._
import vopts.com.amba._
import chisel3._

object AXI4CrossbarUtilities extends RegisteredUtilities[BusCrossbarUtilities] {
  override def utils: BusCrossbarUtilities = new BusCrossbarUtilities {
    override def name: String = "axi4"

    override def masterType: Bundle                =
      Flipped(new AXILiteMasterIO(addrWidth = p(XLen), dataWidth = p(XLen)))
    override def slaveType: Bundle                 =
      Flipped(new AXILiteSlaveIO(addrWidth = p(XLen), dataWidth = p(XLen)))
    override def addressMap: Seq[(BigInt, BigInt)] = p(BusAddressMap)
  }

  override def factory: UtilitiesFactory[BusCrossbarUtilities] = BusCrossbarUtilitiesFactory
}
