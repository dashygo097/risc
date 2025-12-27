package arch.core.regfile

import scala.collection.mutable

object RegfileUtilitiesFactory {
  private val registry = mutable.Map[String, RegfileUtilities]()

  def register(name: String, sigs: RegfileUtilities): Unit =
    registry(name.toLowerCase) = sigs

  def get(name: String): Option[RegfileUtilities] =
    registry.get(name.toLowerCase)

  def getOrElse(name: String, default: RegfileUtilities): RegfileUtilities =
    registry.getOrElse(name.toLowerCase, default)

  def listAvailable(): Seq[String] = registry.keys.toSeq.sorted

  def contains(name: String): Boolean = registry.contains(name.toLowerCase)
}

trait RegisteredRegfileUtilities {
  def isaName: String
  def utils: RegfileUtilities
  RegfileUtilitiesFactory.register(isaName, utils)
}

object RegfileInit {
  val rv32iUtils = RV32IRegfileUtilities
}
