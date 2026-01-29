package arch.system.bridge

import arch.configs._
import vopts.mem.cache._
import chisel3._

trait BusBridgeUtilities extends Utilities {
  def busType: Bundle
  def createBridge[T <: Data](gen: T, memory: CacheIO[T]): Bundle
  def createBridgeReadOnly[T <: Data](gen: T, memory: CacheReadOnlyIO[T]): Bundle
}

object BusBridgeUtilitiesFactory extends UtilitiesFactory[BusBridgeUtilities]("BusBridge")

object BusBridgeInit {
  val axilite = AXILiteBridgeUtilities
  val axifull = AXIFullBridgeUtilities
}
