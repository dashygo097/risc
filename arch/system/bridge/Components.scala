package arch.system.bridge

import arch.configs._
import vopts.mem.cache._
import chisel3._

trait BusBridgeUtilities extends Utilities {
  def busType: Bundle
  def createBridge[T <: Data](gen: T, memory: CacheIO[T], isMmio: Boolean = false): Bundle
  def createBridgeReadOnly[T <: Data](gen: T, memory: CacheReadOnlyIO[T], isMmio: Boolean = false): Bundle
}

object BusBridgeUtilitiesFactory extends UtilitiesFactory[BusBridgeUtilities]("BusBridge")

object BusBridgeInit {
  val axil = AXILiteBridgeUtilities
  val axif = AXIFullBridgeUtilities
}
