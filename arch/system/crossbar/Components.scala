package arch.system.crossbar

import arch.configs._
import chisel3._

trait BusCrossbarUtils extends Utils {
  def masterType: Bundle
  def slaveType: Bundle
  def addressMap: Seq[(Long, Long)]

  def createInterface(ibus: Bundle, dbus: Bundle, mbus: Bundle): Vec[Bundle]
  def connect(ext: Bundle, inner: Bundle): Unit
}

object BusCrossbarUtilsFactory extends UtilsFactory[BusCrossbarUtils]("BusCrossbar")

object BusCrossbarInit {
  val axil = AXILiteCrossbarUtils
  val axif = AXIFullCrossbarUtils
}
