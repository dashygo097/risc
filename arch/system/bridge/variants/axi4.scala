package arch.system.bridge

import arch.configs._
import com.amba._
import mem.cache._
import chisel3._

class AXI4BridgeUtilitiesImpl extends BusBridgeUtilities {
  // TODO: Add a top configuration for this
  def busType(): Bundle                                                     = AXILiteMasterIO(addrWidth = p(XLen), dataWidth = p(XLen))
  def createBus(req: MemoryHierarchyReq, resp: MemoryHierarchyResp): Bundle = {
    val axi4 = Wire(new AXILiteMasterIO(addrWidth = p(XLen), dataWidth = p(XLen)))

    axi4
  }
}

object AXI4BridgeUtilities extends RegisteredBusBridgeUtilities {
  override def busName: String           = "axi4"
  override def utils: BusBridgeUtilities = new AXI4BridgeUtilitiesImpl
}
