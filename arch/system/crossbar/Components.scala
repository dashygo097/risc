package arch.system.crossbar

import arch.configs._
import chisel3._

trait BusCrossbarUtilities extends Utilities {
  def masterType: Bundle
  def slaveType: Bundle
  def addressMap: Seq[(BigInt, BigInt)]
}

object BusCrossbarUtilitiesFactory extends UtilitiesFactory[BusCrossbarUtilities]("BusCrossbar")

object BusCrossbarInit {
  val rv32iUtils = AXI4CrossbarUtilities
}
