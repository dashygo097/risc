package arch.core.decoder

import scala.collection.mutable

object DecoderUtilitiesFactory {
  private val registry = mutable.Map[String, DecoderUtilities]()

  def register(name: String, sigs: DecoderUtilities): Unit =
    registry(name.toLowerCase) = sigs

  def get(name: String): Option[DecoderUtilities] =
    registry.get(name.toLowerCase)

  def getOrElse(name: String, default: DecoderUtilities): DecoderUtilities =
    registry.getOrElse(name.toLowerCase, default)

  def listAvailable(): Seq[String] = registry.keys.toSeq.sorted

  def contains(name: String): Boolean = registry.contains(name.toLowerCase)
}

trait RegisteredDecoderUtilities {
  def isaName: String
  def utils: DecoderUtilities
  DecoderUtilitiesFactory.register(isaName, utils)
}
