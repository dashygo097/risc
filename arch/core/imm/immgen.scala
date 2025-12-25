package arch.core.imm

import scala.collection.mutable

object ImmUtilitiesFactory {
  private val registry = mutable.Map[String, ImmUtilities]()

  def register(name: String, sigs: ImmUtilities): Unit =
    registry(name.toLowerCase) = sigs

  def get(name: String): Option[ImmUtilities] =
    registry.get(name.toLowerCase)

  def getOrElse(name: String, default: ImmUtilities): ImmUtilities =
    registry.getOrElse(name.toLowerCase, default)

  def listAvailable(): Seq[String] = registry.keys.toSeq.sorted

  def contains(name: String): Boolean = registry.contains(name.toLowerCase)
}

trait RegisteredImmUtilities {
  def isaName: String
  def utils: ImmUtilities
  ImmUtilitiesFactory.register(isaName, utils)
}

object ImmInit {
  val rv32Utils = RV32ImmUtilities
}
