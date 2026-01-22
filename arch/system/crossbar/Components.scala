package arch.system.crossbar

import arch.configs._
import chisel3._

trait BusCrossbarUtilities extends Utilities {
  def masterType: Bundle
  def slaveType: Bundle
  def addressMap: Seq[(Long, Long)]

  def createInterface(ibus: Bundle, dbus: Bundle): Vec[Bundle]
  def connectMaster(ext: Bundle, inner: Bundle): Unit
}

object BusCrossbarUtilitiesFactory extends UtilitiesFactory[BusCrossbarUtilities]("BusCrossbar")

object BusCrossbarInit {
  val axilite = AXILiteCrossbarUtilities
  val axifull = AXIFullCrossbarUtilities
}
