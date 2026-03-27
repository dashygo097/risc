package arch.core.decoder

import arch.configs._
import arch.isa._
import chisel3._
import chisel3.util.BitPat

object RV32IMDecoderUtilities extends RegisteredUtilities[DecoderUtilities] with RV32IDecoderConsts {

  private val allEncodings =
    RV32IM.isa.instrSet
      .map(s => s.nop.toSeq ++ s.encodings)
      .getOrElse(Seq.empty)

  private def enc(name: String): BitPat = {
    val e = allEncodings
      .find(_.name == name)
      .getOrElse(throw new NoSuchElementException(s"Instruction '$name' not found in RV32IM"))

    val bits = (p(ILen) - 1 to 0 by -1).map { i =>
      val valueBit = (e.value >> i) & 1
      val maskBit  = (e.mask >> i) & 1
      if (maskBit == 1) valueBit.toString else "?"
    }.mkString

    BitPat("b" + bits)
  }

  override def utils: DecoderUtilities = new DecoderUtilities {
    override def name: String                       = "rv32im"
    override def default: List[BitPat]              = RV32IDecoderUtilities.utils.default
    override def decode(instr: UInt): DecodedOutput = RV32IDecoderUtilities.utils.decode(instr)

    override def table: Array[(BitPat, List[BitPat])] = RV32IMDecoderUtilities.utils.table ++
      Array(
        // R-Type: Mul
        enc("MUL")    -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_X, N, M_X, N, C_X, Y, N, Y, Y),
        enc("MULH")   -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_X, N, M_X, N, C_X, Y, Y, Y, Y),
        enc("MULHSU") -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_X, N, M_X, N, C_X, Y, Y, Y, N),
        enc("MULHU")  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_X, N, M_X, N, C_X, Y, Y, N, N),
      )
  }

  override def factory: UtilitiesFactory[DecoderUtilities] = DecoderUtilitiesFactory
}
