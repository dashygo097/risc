package arch.core.regfile

import utils.Register

trait RegfileUtilities {
  def width: Int
  def extraInfo: Seq[Register]
}
