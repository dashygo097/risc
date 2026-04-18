package arch.configs

import scala.collection.mutable

trait Utils {
  def name: String
}

class UtilsFactory[T <: Utils](val utilityType: String) {
  private val registry = mutable.Map[String, T]()

  def register(utils: T): Unit = registry(utils.name.toLowerCase) = utils

  def get(name: String): Option[T] =
    registry.get(name.toLowerCase)

  def getOrElse(name: String, default: T): T =
    registry.getOrElse(name.toLowerCase, default)

  def getOrThrow(name: String): T =
    registry.getOrElse(
      name.toLowerCase,
      throw new NoSuchElementException(
        s"$utilityType for '$name' not found in registry"
      )
    )

  def listAvailable(): Seq[String] = registry.keys.toSeq.sorted

  def contains(name: String): Boolean = registry.contains(name.toLowerCase)

  def getAll(): Seq[T] = registry.values.toSeq
}

trait RegisteredUtils[T <: Utils] {
  def utils: T
  def factory: UtilsFactory[T]

  factory.register(utils)
}
