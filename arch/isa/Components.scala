package arch.isa

import arch.configs.proto._
import chisel3.util.BitPat
import scala.collection.mutable.LinkedHashMap

abstract class IsaWrapper {
  def isa: Isa
  final def name: String         = isa.name
  final def xlen: Int            = isa.xlen.toInt
  final def ilen: Int            = isa.ilen.toInt
  final def iAlign: Int          = isa.iAlign.toInt
  final def numArchRegs: Int     = isa.numArchRegs.toInt
  final def isBigEndian: Boolean = isa.isBigEndian

  final def bubble: BitPat = {
    val nop = isa.instrSet
      .flatMap(_.nop)
      .getOrElse(throw new Exception(s"ISA '$name' has no NOP defined"))

    val bits = (ilen - 1 to 0 by -1).map { i =>
      val valueBit = (nop.value >> i) & 1
      val maskBit  = (nop.mask >> i) & 1
      if (maskBit == 1) valueBit.toString else "?"
    }.mkString

    BitPat("b" + bits)
  }

}

object IsaFactory {
  private val registry = LinkedHashMap.empty[String, IsaWrapper]

  def register(isa: IsaWrapper): Unit = {
    require(!registry.contains(isa.name), s"ISA '${isa.name}' already registered")
    registry(isa.name) = isa
  }

  def fromString(name: String): Option[IsaWrapper] =
    registry.get(name.toLowerCase)

  def available: Seq[IsaWrapper] = registry.values.toSeq

  private def get(name: String): IsaWrapper =
    fromString(name).getOrElse(
      throw new Exception(
        s"Unknown Isa: '$name'. Available: ${available.map(_.name).mkString(", ")}"
      )
    )

  def isa(isa: String): Isa             = get(isa).isa
  def xlen(isa: String): Int            = get(isa).xlen
  def ilen(isa: String): Int            = get(isa).ilen
  def iAlign(isa: String): Int          = get(isa).iAlign
  def numArchRegs(isa: String): Int     = get(isa).numArchRegs
  def isBigEndian(isa: String): Boolean = get(isa).isBigEndian
  def bubble(isa: String): BitPat       = get(isa).bubble
}
