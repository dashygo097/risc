package arch.core.decoder.riscv

import arch.core.mult.riscv._
import arch.core.decoder._
import arch.configs._
import arch.isa._
import chisel3._
import chisel3.util.BitPat

trait RV32IMUOp extends RV32IUOp with RV32IMMultUOpConsts {}

object RV32IMDecoderUtils extends RegisteredUtils[DecoderUtils] with RV32IMUOp {

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

  override def utils: DecoderUtils = new DecoderUtils {
    override def name: String          = "rv32im"
    override def default: List[BitPat] = RV32IDecoderUtils.utils.default

    override def decode(instr: UInt): DecodedOutput = {
      val sigs    = Wire(new DecodedOutput)
      val decoder = DecodeLogic(instr, default, table)

      val is_div = DecodeLogic(
        instr,
        N,
        Array(
          enc("DIV")  -> Y,
          enc("DIVU") -> Y,
          enc("REM")  -> Y,
          enc("REMU") -> Y,
        )
      ).asBool

      sigs.legal    := decoder(0).asBool || is_div
      sigs.regwrite := decoder(1).asBool || is_div
      sigs.imm_type := decoder(2)

      sigs.alu  := decoder(3).asBool
      sigs.mult := decoder(4).asBool || is_div
      sigs.lsu  := decoder(5).asBool
      sigs.bru  := decoder(6).asBool
      sigs.csr  := decoder(7).asBool
      sigs.ret  := decoder(8).asBool

      sigs.uop := decoder(9)

      sigs
    }

    override def table: Array[(BitPat, List[BitPat])] = RV32IDecoderUtils.utils.table ++
      Array(
        // R-Type: Mul
        enc("MUL")    -> List(Y, N, IMM_X, N, N, N, N, N, Y, UOP_MUL),
        enc("MULH")   -> List(Y, N, IMM_X, N, N, N, N, N, Y, UOP_MULH),
        enc("MULHSU") -> List(Y, N, IMM_X, N, N, N, N, N, Y, UOP_MULHSU),
        enc("MULHU")  -> List(Y, N, IMM_X, N, N, N, N, N, Y, UOP_MULHU)
      )
  }

  override def factory: UtilsFactory[DecoderUtils] = DecoderUtilsFactory
}
