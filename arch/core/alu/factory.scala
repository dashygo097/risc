package arch.core.alu

import scala.collection.mutable

object ALUUtilitiesFactory {
  private val registry = mutable.Map[String, ALUUtilities]()

  def register(name: String, sigs: ALUUtilities): Unit =
    registry(name.toLowerCase) = sigs

  def get(name: String): Option[ALUUtilities] =
    registry.get(name.toLowerCase)

  def getOrElse(name: String, default: ALUUtilities): ALUUtilities =
    registry.getOrElse(name.toLowerCase, default)

  def listAvailable(): Seq[String] = registry.keys.toSeq.sorted

  def contains(name: String): Boolean = registry.contains(name.toLowerCase)
}

trait RegisteredALUUtilities {
  def isaName: String
  def utils: ALUUtilities
  ALUUtilitiesFactory.register(isaName, utils)
}

object ALUInit {
  val rv32iUtils = RV32IALUUtilities
}
