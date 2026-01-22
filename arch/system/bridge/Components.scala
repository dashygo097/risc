package arch.system.bridge

import arch.configs._
import vopts.mem.cache._
import chisel3._

trait BusBridgeUtilities extends Utilities {
  def busType: Bundle
  def createBridge(memory: UnifiedMemoryIO): Bundle
  def createBridgeReadOnly(memory: UnifiedMemoryReadOnlyIO): Bundle
}

object BusBridgeUtilitiesFactory extends UtilitiesFactory[BusBridgeUtilities]("BusBridge")

object BusBridgeInit {
  val axilite = AXILiteBridgeUtilities
}
