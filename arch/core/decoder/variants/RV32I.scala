package arch.core.decoder.riscv

import arch.core.imm.riscv._
import arch.core.alu.riscv._
import arch.core.lsu.riscv._
import arch.core.bru.riscv._
import arch.core.csr.riscv._
import arch.core.decoder._
import arch.configs._
import arch.isa._
import chisel3._
import chisel3.util.BitPat

trait RV32IUOp extends RV32IImmConsts with RV32IAluUopConsts with RV32IMemUOpConsts with RV32ICsrUOpConsts with RV32IBruUOpConsts {
  def UOP_X = BitPat("b????????")

  def X = BitPat("b?")
  def Y = BitPat("b1")
  def N = BitPat("b0")
}

object RV32IDecoderUtils extends RegisteredUtils[DecoderUtils] with RV32IUOp {
  private val allEncodings =
    RV32I.isa.instrSet
      .map(s => s.nop.toSeq ++ s.encodings)
      .getOrElse(Seq.empty)

  private def enc(name: String): BitPat = {
    val e = allEncodings
      .find(_.name == name)
      .getOrElse(throw new NoSuchElementException(s"Instruction '$name' not found in RV32I"))

    val bits = (p(ILen) - 1 to 0 by -1).map { i =>
      val valueBit = (e.value >> i) & 1
      val maskBit  = (e.mask >> i) & 1
      if (maskBit == 1) valueBit.toString else "?"
    }.mkString

    BitPat("b" + bits)
  }

  override def utils: DecoderUtils = new DecoderUtils {
    override def name: String = "rv32i"

    override def default: List[BitPat] =
      List(N, X, IMM_X, X, X, X, X, X, X, X, UOP_X)

    override def decode(instr: UInt): DecodedOutput = {
      val sigs    = Wire(new DecodedOutput)
      val decoder = DecodeLogic(instr, default, table)

      sigs.legal    := decoder(0).asBool
      sigs.regwrite := decoder(1).asBool
      sigs.imm_type := decoder(2)

      sigs.alu   := decoder(3).asBool
      sigs.mult  := decoder(4).asBool
      sigs.div   := decoder(5).asBool
      sigs.load  := decoder(6).asBool
      sigs.store := decoder(7).asBool
      sigs.bru   := decoder(8).asBool
      sigs.csr   := decoder(9).asBool

      sigs.uop := decoder(10)

      sigs
    }

    override def table: Array[(BitPat, List[BitPat])] = Array(
      enc("ADD")    -> List(Y, Y, IMM_X, Y, N, N, N, N, N, N, UOP_ADD),
      enc("SUB")    -> List(Y, Y, IMM_X, Y, N, N, N, N, N, N, UOP_SUB),
      enc("SLL")    -> List(Y, Y, IMM_X, Y, N, N, N, N, N, N, UOP_SLL),
      enc("SLT")    -> List(Y, Y, IMM_X, Y, N, N, N, N, N, N, UOP_SLT),
      enc("SLTU")   -> List(Y, Y, IMM_X, Y, N, N, N, N, N, N, UOP_SLTU),
      enc("XOR")    -> List(Y, Y, IMM_X, Y, N, N, N, N, N, N, UOP_XOR),
      enc("SRL")    -> List(Y, Y, IMM_X, Y, N, N, N, N, N, N, UOP_SRL),
      enc("SRA")    -> List(Y, Y, IMM_X, Y, N, N, N, N, N, N, UOP_SRA),
      enc("OR")     -> List(Y, Y, IMM_X, Y, N, N, N, N, N, N, UOP_OR),
      enc("AND")    -> List(Y, Y, IMM_X, Y, N, N, N, N, N, N, UOP_AND),
      enc("ADDI")   -> List(Y, Y, IMM_I, Y, N, N, N, N, N, N, UOP_ADDI),
      enc("SLLI")   -> List(Y, Y, IMM_I, Y, N, N, N, N, N, N, UOP_SLLI),
      enc("SLTI")   -> List(Y, Y, IMM_I, Y, N, N, N, N, N, N, UOP_SLTI),
      enc("SLTIU")  -> List(Y, Y, IMM_I, Y, N, N, N, N, N, N, UOP_SLTIU),
      enc("XORI")   -> List(Y, Y, IMM_I, Y, N, N, N, N, N, N, UOP_XORI),
      enc("SRLI")   -> List(Y, Y, IMM_I, Y, N, N, N, N, N, N, UOP_SRLI),
      enc("SRAI")   -> List(Y, Y, IMM_I, Y, N, N, N, N, N, N, UOP_SRAI),
      enc("ORI")    -> List(Y, Y, IMM_I, Y, N, N, N, N, N, N, UOP_ORI),
      enc("ANDI")   -> List(Y, Y, IMM_I, Y, N, N, N, N, N, N, UOP_ANDI),
      enc("LB")     -> List(Y, Y, IMM_I, N, N, N, Y, N, N, N, UOP_LB),
      enc("LH")     -> List(Y, Y, IMM_I, N, N, N, Y, N, N, N, UOP_LH),
      enc("LW")     -> List(Y, Y, IMM_I, N, N, N, Y, N, N, N, UOP_LW),
      enc("LBU")    -> List(Y, Y, IMM_I, N, N, N, Y, N, N, N, UOP_LBU),
      enc("LHU")    -> List(Y, Y, IMM_I, N, N, N, Y, N, N, N, UOP_LHU),
      enc("JALR")   -> List(Y, Y, IMM_I, N, N, N, N, N, Y, N, UOP_JALR),
      enc("CSRRW")  -> List(Y, Y, IMM_I, N, N, N, N, N, N, Y, UOP_CSRRW),
      enc("CSRRS")  -> List(Y, Y, IMM_I, N, N, N, N, N, N, Y, UOP_CSRRS),
      enc("CSRRC")  -> List(Y, Y, IMM_I, N, N, N, N, N, N, Y, UOP_CSRRC),
      enc("CSRRWI") -> List(Y, Y, IMM_I, N, N, N, N, N, N, Y, UOP_CSRRWI),
      enc("CSRRSI") -> List(Y, Y, IMM_I, N, N, N, N, N, N, Y, UOP_CSRRSI),
      enc("CSRRCI") -> List(Y, Y, IMM_I, N, N, N, N, N, N, Y, UOP_CSRRCI),
      enc("SB")     -> List(Y, N, IMM_S, N, N, N, N, Y, N, N, UOP_SB),
      enc("SH")     -> List(Y, N, IMM_S, N, N, N, N, Y, N, N, UOP_SH),
      enc("SW")     -> List(Y, N, IMM_S, N, N, N, N, Y, N, N, UOP_SW),
      enc("BEQ")    -> List(Y, N, IMM_B, N, N, N, N, N, Y, N, UOP_BEQ),
      enc("BNE")    -> List(Y, N, IMM_B, N, N, N, N, N, Y, N, UOP_BNE),
      enc("BLT")    -> List(Y, N, IMM_B, N, N, N, N, N, Y, N, UOP_BLT),
      enc("BGE")    -> List(Y, N, IMM_B, N, N, N, N, N, Y, N, UOP_BGE),
      enc("BLTU")   -> List(Y, N, IMM_B, N, N, N, N, N, Y, N, UOP_BLTU),
      enc("BGEU")   -> List(Y, N, IMM_B, N, N, N, N, N, Y, N, UOP_BGEU),
      enc("LUI")    -> List(Y, Y, IMM_U, Y, N, N, N, N, N, N, UOP_LUI),
      enc("AUIPC")  -> List(Y, Y, IMM_U, Y, N, N, N, N, N, N, UOP_AUIPC),
      enc("JAL")    -> List(Y, Y, IMM_J, N, N, N, N, N, Y, N, UOP_JAL),
      enc("MRET")   -> List(Y, N, IMM_X, N, N, N, N, N, N, Y, UOP_MRET)
    )
  }

  override def factory: UtilsFactory[DecoderUtils] = DecoderUtilsFactory
}
