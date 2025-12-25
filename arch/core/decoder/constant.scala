package arch.core.decoder

import chisel3.util.BitPat

abstract trait DecodeConsts {
  val table: Array[(BitPat, List[BitPat])]
}
