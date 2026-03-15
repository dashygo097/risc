package arch.core.decoder

import arch.core.imm.RV32IImmConsts
import arch.core.alu.RV32IAluConsts
import arch.core.lsu.RV32ILsuConsts
import arch.core.bru.RV32IBranchConsts
import arch.core.csr.RV32ICsrConsts
import arch.configs._
import arch.isa._
import chisel3._
import chisel3.util._

object RV32IDecoderUtilities extends RegisteredUtilities[DecoderUtilities] with RV32IAluConsts with RV32ILsuConsts with RV32IImmConsts with RV32IBranchConsts with RV32ICsrConsts {

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
      List(N, N, IMM_X, X, BR_X, X, A1_X, A2_X, X, AFN_X, X, M_X, X, C_X)

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

      sigs
    }

    override def table: Array[(BitPat, List[BitPat])] = Array(
      // R-Type
      enc("ADD")    -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_ADD, N, M_X, N, C_X),
      enc("SUB")    -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, Y, AFN_ADD, N, M_X, N, C_X),
      enc("SLL")    -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_SLL, N, M_X, N, C_X),
      enc("SLT")    -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_SLT, N, M_X, N, C_X),
      enc("SLTU")   -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_SLTU, N, M_X, N, C_X),
      enc("XOR")    -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_XOR, N, M_X, N, C_X),
      enc("SRL")    -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_SRL, N, M_X, N, C_X),
      enc("SRA")    -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, Y, AFN_SRL, N, M_X, N, C_X),
      enc("OR")     -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_OR, N, M_X, N, C_X),
      enc("AND")    -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_AND, N, M_X, N, C_X),
      // I-Type: Arithmetic
      enc("ADDI")   -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
      enc("SLLI")   -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_SLL, N, M_X, N, C_X),
      enc("SLTI")   -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_SLT, N, M_X, N, C_X),
      enc("SLTIU")  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_SLTU, N, M_X, N, C_X),
      enc("XORI")   -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_XOR, N, M_X, N, C_X),
      enc("SRLI")   -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_SRL, N, M_X, N, C_X),
      enc("SRAI")   -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, Y, AFN_SRL, N, M_X, N, C_X),
      enc("ORI")    -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_OR, N, M_X, N, C_X),
      enc("ANDI")   -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_AND, N, M_X, N, C_X),
      // I-Type: Load
      enc("LB")     -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LB, N, C_X),
      enc("LH")     -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LH, N, C_X),
      enc("LW")     -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LW, N, C_X),
      enc("LBU")    -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LBU, N, C_X),
      enc("LHU")    -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LHU, N, C_X),
      // I-Type: Jump
      enc("JALR")   -> List(Y, Y, IMM_I, Y, BR_JALR, Y, A1_PC, A2_PCSTEP, N, AFN_ADD, N, M_X, N, C_X),
      // I-Type: CSR
      enc("CSRRW")  -> List(Y, Y, IMM_I, N, BR_X, N, A1_RS1, A2_IMM, N, AFN_X, N, M_X, Y, C_RW),
      enc("CSRRS")  -> List(Y, Y, IMM_I, N, BR_X, N, A1_RS1, A2_IMM, N, AFN_X, N, M_X, Y, C_RS),
      enc("CSRRC")  -> List(Y, Y, IMM_I, N, BR_X, N, A1_RS1, A2_IMM, N, AFN_X, N, M_X, Y, C_RC),
      enc("CSRRWI") -> List(Y, Y, IMM_I, N, BR_X, N, A1_ZERO, A2_IMM, N, AFN_X, N, M_X, Y, C_RWI),
      enc("CSRRSI") -> List(Y, Y, IMM_I, N, BR_X, N, A1_ZERO, A2_IMM, N, AFN_X, N, M_X, Y, C_RSI),
      enc("CSRRCI") -> List(Y, Y, IMM_I, N, BR_X, N, A1_ZERO, A2_IMM, N, AFN_X, N, M_X, Y, C_RCI),
      // S-Type
      enc("SB")     -> List(Y, N, IMM_S, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_SB, N, C_X),
      enc("SH")     -> List(Y, N, IMM_S, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_SH, N, C_X),
      enc("SW")     -> List(Y, N, IMM_S, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_SW, N, C_X),
      // B-Type
      enc("BEQ")    -> List(Y, N, IMM_B, Y, BR_EQ, N, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
      enc("BNE")    -> List(Y, N, IMM_B, Y, BR_NE, N, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
      enc("BLT")    -> List(Y, N, IMM_B, Y, BR_LT, N, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
      enc("BGE")    -> List(Y, N, IMM_B, Y, BR_GE, N, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
      enc("BLTU")   -> List(Y, N, IMM_B, Y, BR_LTU, N, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
      enc("BGEU")   -> List(Y, N, IMM_B, Y, BR_GEU, N, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
      // U-Type
      enc("LUI")    -> List(Y, Y, IMM_U, N, BR_X, Y, A1_ZERO, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
      enc("AUIPC")  -> List(Y, Y, IMM_U, N, BR_X, Y, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
      // J-Type
      enc("JAL")    -> List(Y, Y, IMM_J, Y, BR_JAL, Y, A1_PC, A2_PCSTEP, N, AFN_ADD, N, M_X, N, C_X),
    )
  }

  override def factory: UtilitiesFactory[DecoderUtilities] = DecoderUtilitiesFactory
}
