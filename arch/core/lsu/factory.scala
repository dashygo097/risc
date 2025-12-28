package arch.core.lsu

import scala.collection.mutable

object LsuUtilitiesFactory {
  private val registry = mutable.Map[String, LsuUtilities]()

  def register(name: String, sigs: LsuUtilities): Unit =
    registry(name.toLowerCase) = sigs

  def get(name: String): Option[LsuUtilities] =
    registry.get(name.toLowerCase)

  def getOrElse(name: String, default: LsuUtilities): LsuUtilities =
    registry.getOrElse(name.toLowerCase, default)

  def getOrThrow(name: String): LsuUtilities =
    registry.getOrElse(
      name.toLowerCase,
      throw new NoSuchElementException(s"LsuUtilities for ISA '$name' not found")
    )

  def listAvailable(): Seq[String] = registry.keys.toSeq.sorted

  def contains(name: String): Boolean = registry.contains(name.toLowerCase)
}

trait RegisteredLsuUtilities {
  def isaName: String
  def utils: LsuUtilities
  LsuUtilitiesFactory.register(isaName, utils)
}

object LsuInit {
  val rv32iUtils = RV32ILsuUtilities
}
