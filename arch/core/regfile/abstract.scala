package arch.core.regfile

import utils.Register

trait RegfileUtilities {
  def numGPR: Int
  def extraInfo: Seq[Register]
}
