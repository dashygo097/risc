package arch.core.decoder

import arch.core.imm._
import arch.core.alu._
import arch.core.lsu._
import arch.core.bru._
import arch.core.csr._
import arch.configs._
import arch.isa._
import chisel3._
import chisel3.util.BitPat

trait RV32IUOp extends RV32IImmConsts with RV32IAluUopConsts with RV32ILsuUOpConsts with RV32ICsrUOpConsts with RV32IBruUOpConsts {
  def UOP_X = BitPat("b????????")

  // Common Booleans
  def X = BitPat("b?")
  def Y = BitPat("b1")
  def N = BitPat("b0")

  override def UOP_JAL  = super[RV32IBruUOpConsts].UOP_JAL
  override def UOP_JALR = super[RV32IBruUOpConsts].UOP_JALR
}

object RV32IDecoderUtilities extends RegisteredUtilities[DecoderUtilities] with RV32IUOp {

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

  override def utils: DecoderUtilities = new DecoderUtilities {
    override def name: String = "rv32i"

    override def default: List[BitPat] =
      List(N, N, IMM_X, X, X, X, X, X, X, UOP_X)

    override def decode(instr: UInt): DecodedOutput = {
      val sigs    = Wire(new DecodedOutput)
      val decoder = DecodeLogic(instr, default, table)

      sigs.legal    := decoder(0).asBool
      sigs.regwrite := decoder(1).asBool
      sigs.imm_type := decoder(2)

      sigs.alu  := decoder(3).asBool
      sigs.mult := decoder(4).asBool
      sigs.lsu  := decoder(5).asBool
      sigs.bru  := decoder(6).asBool
      sigs.csr  := decoder(7).asBool
      sigs.ret  := decoder(8).asBool

      sigs.uop := decoder(9)

      sigs
    }

    override def table: Array[(BitPat, List[BitPat])] = Array(
      // R-Type
      enc("ADD")  -> List(Y, Y, IMM_X, Y, N, N, N, N, N, UOP_ADD),
      enc("SUB")  -> List(Y, Y, IMM_X, Y, N, N, N, N, N, UOP_SUB),
      enc("SLL")  -> List(Y, Y, IMM_X, Y, N, N, N, N, N, UOP_SLL),
      enc("SLT")  -> List(Y, Y, IMM_X, Y, N, N, N, N, N, UOP_SLT),
      enc("SLTU") -> List(Y, Y, IMM_X, Y, N, N, N, N, N, UOP_SLTU),
      enc("XOR")  -> List(Y, Y, IMM_X, Y, N, N, N, N, N, UOP_XOR),
      enc("SRL")  -> List(Y, Y, IMM_X, Y, N, N, N, N, N, UOP_SRL),
      enc("SRA")  -> List(Y, Y, IMM_X, Y, N, N, N, N, N, UOP_SRA),
      enc("OR")   -> List(Y, Y, IMM_X, Y, N, N, N, N, N, UOP_OR),
      enc("AND")  -> List(Y, Y, IMM_X, Y, N, N, N, N, N, UOP_AND),

      // I-Type: Arithmetic
      enc("ADDI")  -> List(Y, Y, IMM_I, Y, N, N, N, N, N, UOP_ADDI),
      enc("SLLI")  -> List(Y, Y, IMM_I, Y, N, N, N, N, N, UOP_SLLI),
      enc("SLTI")  -> List(Y, Y, IMM_I, Y, N, N, N, N, N, UOP_SLTI),
      enc("SLTIU") -> List(Y, Y, IMM_I, Y, N, N, N, N, N, UOP_SLTIU),
      enc("XORI")  -> List(Y, Y, IMM_I, Y, N, N, N, N, N, UOP_XORI),
      enc("SRLI")  -> List(Y, Y, IMM_I, Y, N, N, N, N, N, UOP_SRLI),
      enc("SRAI")  -> List(Y, Y, IMM_I, Y, N, N, N, N, N, UOP_SRAI),
      enc("ORI")   -> List(Y, Y, IMM_I, Y, N, N, N, N, N, UOP_ORI),
      enc("ANDI")  -> List(Y, Y, IMM_I, Y, N, N, N, N, N, UOP_ANDI),

      // I-Type: Load
      enc("LB")  -> List(Y, Y, IMM_I, N, N, Y, N, N, N, UOP_LB),
      enc("LH")  -> List(Y, Y, IMM_I, N, N, Y, N, N, N, UOP_LH),
      enc("LW")  -> List(Y, Y, IMM_I, N, N, Y, N, N, N, UOP_LW),
      enc("LBU") -> List(Y, Y, IMM_I, N, N, Y, N, N, N, UOP_LBU),
      enc("LHU") -> List(Y, Y, IMM_I, N, N, Y, N, N, N, UOP_LHU),

      // I-Type: Jump
      enc("JALR") -> List(Y, Y, IMM_I, N, N, N, Y, N, N, UOP_JALR),

      // I-Type: CSR
      enc("CSRRW")  -> List(Y, Y, IMM_I, N, N, N, N, Y, N, UOP_CSRRW),
      enc("CSRRS")  -> List(Y, Y, IMM_I, N, N, N, N, Y, N, UOP_CSRRS),
      enc("CSRRC")  -> List(Y, Y, IMM_I, N, N, N, N, Y, N, UOP_CSRRC),
      enc("CSRRWI") -> List(Y, Y, IMM_I, N, N, N, N, Y, N, UOP_CSRRWI),
      enc("CSRRSI") -> List(Y, Y, IMM_I, N, N, N, N, Y, N, UOP_CSRRSI),
      enc("CSRRCI") -> List(Y, Y, IMM_I, N, N, N, N, Y, N, UOP_CSRRCI),

      // S-Type
      enc("SB") -> List(Y, N, IMM_S, N, N, Y, N, N, N, UOP_SB),
      enc("SH") -> List(Y, N, IMM_S, N, N, Y, N, N, N, UOP_SH),
      enc("SW") -> List(Y, N, IMM_S, N, N, Y, N, N, N, UOP_SW),

      // B-Type
      enc("BEQ")  -> List(Y, N, IMM_B, N, N, N, Y, N, N, UOP_BEQ),
      enc("BNE")  -> List(Y, N, IMM_B, N, N, N, Y, N, N, UOP_BNE),
      enc("BLT")  -> List(Y, N, IMM_B, N, N, N, Y, N, N, UOP_BLT),
      enc("BGE")  -> List(Y, N, IMM_B, N, N, N, Y, N, N, UOP_BGE),
      enc("BLTU") -> List(Y, N, IMM_B, N, N, N, Y, N, N, UOP_BLTU),
      enc("BGEU") -> List(Y, N, IMM_B, N, N, N, Y, N, N, UOP_BGEU),

      // U-Type
      enc("LUI")   -> List(Y, Y, IMM_U, Y, N, N, N, N, N, UOP_LUI),
      enc("AUIPC") -> List(Y, Y, IMM_U, Y, N, N, N, N, N, UOP_AUIPC),

      // J-Type
      enc("JAL") -> List(Y, Y, IMM_J, N, N, N, Y, N, N, UOP_JAL),

      // SYSTEM
      enc("MRET") -> List(Y, N, IMM_X, N, N, N, N, N, Y, UOP_MRET)
    )
  }

  override def factory: UtilitiesFactory[DecoderUtilities] = DecoderUtilitiesFactory
}
