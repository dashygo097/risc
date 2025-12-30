package arch.core.bru

import scala.collection.mutable

object BruUtilitiesFactory {
  private val registry = mutable.Map[String, BruUtilities]()

  def register(name: String, utils: BruUtilities): Unit =
    registry(name.toLowerCase) = utils

  def get(name: String): Option[BruUtilities] =
    registry.get(name.toLowerCase)

  def getOrElse(name: String, default: BruUtilities): BruUtilities =
    registry.getOrElse(name.toLowerCase, default)

  def getOrThrow(name: String): BruUtilities =
    registry.getOrElse(
      name.toLowerCase,
      throw new NoSuchElementException(s"BruUtilities for ISA '$name' not found")
    )

  def listAvailable(): Seq[String] = registry.keys.toSeq.sorted

  def contains(name: String): Boolean = registry.contains(name.toLowerCase)
}

trait RegisteredBruUtilities {
  def isaName: String
  def utils: BruUtilities
  BruUtilitiesFactory.register(isaName, utils)
}

object BruInit {
  val rv32Utils = RV32BruUtilities
}
