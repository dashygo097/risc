package arch.core.alu

import scala.collection.mutable

object AluUtilitiesFactory {
  private val registry = mutable.Map[String, AluUtilities]()

  def register(name: String, sigs: AluUtilities): Unit =
    registry(name.toLowerCase) = sigs

  def get(name: String): Option[AluUtilities] =
    registry.get(name.toLowerCase)

  def getOrElse(name: String, default: AluUtilities): AluUtilities =
    registry.getOrElse(name.toLowerCase, default)

  def getOrThrow(name: String): AluUtilities =
    registry.getOrElse(
      name.toLowerCase,
      throw new NoSuchElementException(s"AluUtilities for ISA '$name' not found")
    )

  def listAvailable(): Seq[String] = registry.keys.toSeq.sorted

  def contains(name: String): Boolean = registry.contains(name.toLowerCase)
}

trait RegisteredAluUtilities {
  def isaName: String
  def utils: AluUtilities
  AluUtilitiesFactory.register(isaName, utils)
}

object AluInit {
  val rv32iUtils = RV32IAluUtilities
}
