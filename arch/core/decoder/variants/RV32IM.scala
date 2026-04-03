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
    override def name: String          = "rv32im"
    override def default: List[BitPat] = RV32IDecoderUtilities.utils.default

    override def decode(instr: UInt): DecodedOutput = {
      val sigs    = Wire(new DecodedOutput)
      val decoder = DecodeLogic(instr, default, table)

      sigs.legal    := decoder(0).asBool
      sigs.regwrite := decoder(1).asBool
      sigs.imm_type := decoder(2)
      sigs.branch   := decoder(3).asBool
      sigs.br_type  := decoder(4)
      sigs.alu      := decoder(5).asBool
      sigs.alu_sel1 := decoder(6)
      sigs.alu_sel2 := decoder(7)
      sigs.alu_mode := decoder(8).asBool
      sigs.alu_fn   := decoder(9)
      sigs.lsu      := decoder(10).asBool
      sigs.lsu_cmd  := decoder(11)
      sigs.csr      := decoder(12).asBool
      sigs.csr_cmd  := decoder(13)

      sigs.mul_en       := decoder(14).asBool
      sigs.mul_high     := decoder(15).asBool
      sigs.mul_a_signed := decoder(16).asBool
      sigs.mul_b_signed := decoder(17).asBool

      sigs.ret := decoder(18).asBool

      sigs
    }

    override def table: Array[(BitPat, List[BitPat])] = RV32IDecoderUtilities.utils.table ++
      Array(
        // R-Type: Mul
        enc("MUL")    -> List(Y, Y, IMM_X, N, BR_X, N, A1_X, A2_X, N, AFN_X, N, M_X, N, C_X, Y, N, Y, Y, N),
        enc("MULH")   -> List(Y, Y, IMM_X, N, BR_X, N, A1_X, A2_X, N, AFN_X, N, M_X, N, C_X, Y, Y, Y, Y, N),
        enc("MULHSU") -> List(Y, Y, IMM_X, N, BR_X, N, A1_X, A2_X, N, AFN_X, N, M_X, N, C_X, Y, Y, Y, N, N),
        enc("MULHU")  -> List(Y, Y, IMM_X, N, BR_X, N, A1_X, A2_X, N, AFN_X, N, M_X, N, C_X, Y, Y, N, N, N),
      )
  }

  override def factory: UtilitiesFactory[DecoderUtilities] = DecoderUtilitiesFactory
}
