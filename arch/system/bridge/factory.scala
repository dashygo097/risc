package arch.system.bridge

import scala.collection.mutable

object BusBridgeUtilitiesFactory {
  private val registry = mutable.Map[String, BusBridgeUtilities]()

  def register(name: String, sigs: BusBridgeUtilities): Unit =
    registry(name.toLowerCase) = sigs

  def get(name: String): Option[BusBridgeUtilities] =
    registry.get(name.toLowerCase)

  def getOrElse(name: String, default: BusBridgeUtilities): BusBridgeUtilities =
    registry.getOrElse(name.toLowerCase, default)

  def getOrThrow(name: String): BusBridgeUtilities =
    registry.getOrElse(
      name.toLowerCase,
      throw new NoSuchElementException(s"BusBridgeUtilities for ISA '$name' not found")
    )

  def listAvailable(): Seq[String] = registry.keys.toSeq.sorted

  def contains(name: String): Boolean = registry.contains(name.toLowerCase)
}

trait RegisteredBusBridgeUtilities {
  def busName: String
  def utils: BusBridgeUtilities
  BusBridgeUtilitiesFactory.register(busName, utils)
}

object BusBridgeInit {
  val axi4Utils = AXI4BridgeUtilities
}
