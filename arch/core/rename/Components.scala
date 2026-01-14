package arch.core.rename

import arch.configs._
import chisel3._

trait RenameUtilities extends Utilities {}

object RenameUtilitiesFactory extends UtilitiesFactory[RenameUtilities]("Rename")

object RenameInit {
  val rv32iUtils = RV32IRenameUtilities
}
