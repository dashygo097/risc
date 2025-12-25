package arch.core.decoder

import scala.collection.mutable

object DecodeTableFactory {
  private val registry = mutable.Map[String, DecodeTable]()

  def register(name: String, decoder: DecodeTable): Unit =
    registry(name.toLowerCase) = decoder

  def get(name: String): Option[DecodeTable] =
    registry.get(name.toLowerCase)

  def getOrElse(name: String, default: DecodeTable): DecodeTable =
    registry.getOrElse(name.toLowerCase, default)

  def listAvailable(): Seq[String] = registry.keys.toSeq.sorted

  def contains(name: String): Boolean = registry.contains(name.toLowerCase)
}

object DecodeCtrlSigsFactory {
  private val registry = mutable.Map[String, DecodeCtrlSigs]()

  def register(name: String, sigs: DecodeCtrlSigs): Unit =
    registry(name.toLowerCase) = sigs

  def get(name: String): Option[DecodeCtrlSigs] =
    registry.get(name.toLowerCase)

  def getOrElse(name: String, default: DecodeCtrlSigs): DecodeCtrlSigs =
    registry.getOrElse(name.toLowerCase, default)

  def listAvailable(): Seq[String] = registry.keys.toSeq.sorted

  def contains(name: String): Boolean = registry.contains(name.toLowerCase)
}

trait RegisteredDecodeTable extends DecodeTable {
  def isaName: String
  DecodeTableFactory.register(isaName, this)
}

trait RegisteredDecodeCtrlSigs extends DecodeCtrlSigs {
  def isaName: String
  DecodeCtrlSigsFactory.register(isaName, this)
}
