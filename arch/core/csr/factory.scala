package arch.core.csr

import scala.collection.mutable

object CsrUtilitiesFactory {
  private val registry = mutable.Map[String, CsrUtilities]()

  def register(name: String, sigs: CsrUtilities): Unit =
    registry(name.toLowerCase) = sigs

  def get(name: String): Option[CsrUtilities] =
    registry.get(name.toLowerCase)

  def getOrElse(name: String, default: CsrUtilities): CsrUtilities =
    registry.getOrElse(name.toLowerCase, default)

  def getOrThrow(name: String): CsrUtilities =
    registry.getOrElse(
      name.toLowerCase,
      throw new NoSuchElementException(s"CsrUtilities for ISA '$name' not found")
    )

  def listAvailable(): Seq[String] = registry.keys.toSeq.sorted

  def contains(name: String): Boolean = registry.contains(name.toLowerCase)
}

trait RegisteredCsrUtilities {
  def isaName: String
  def utils: CsrUtilities
  CsrUtilitiesFactory.register(isaName, utils)
}

object CsrInit {
  val rv32iUtils = RV32ICsrUtilities
}
