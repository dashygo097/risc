package arch.system.bridge

import mem.cache._
import chisel3._

trait BusBridgeUtilities {
  def busType(): Bundle
  def createBridge(memory: UnifiedMemoryIO): Bundle
}
