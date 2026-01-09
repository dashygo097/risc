package arch.core.decoder

import arch.core.common.Consts
import arch.core.imm.RV32IImmConsts
import arch.core.alu.RV32IAluConsts
import arch.core.lsu.RV32ILsuConsts
import arch.core.regfile.RV32IRegfileConsts
import arch.core.bru.RV32IBranchConsts
import arch.core.csr.RV32ICsrConsts
import arch.configs._
import arch.isa.RV32I
import chisel3._
import chisel3.util._

trait RV32IDecodeConsts extends Consts with RV32IAluConsts with RV32ILsuConsts with RV32IImmConsts with RV32IRegfileConsts with RV32IBranchConsts with RV32ICsrConsts

object RV32IDecoderUtilities extends RegisteredUtilities[DecoderUtilities] with RV32IDecodeConsts {
  override def utils: DecoderUtilities = new DecoderUtilities {
    override def name: String = "rv32i"

    override def default: List[BitPat] =
      List(N, N, IMM_X, X, BR_X, X, A1_X, A2_X, X, AFN_X, X, M_X, X, C_X)
      //   ^  ^  ^      ^  ^     ^  ^     ^     ^  ^      ^  ^    ^  ^
      //   |  |  |      |  |     |  |     |     |  |      |  |    |  +-- csr_cmd
      //   |  |  |      |  |     |  |     |     |  |      |  |    +-- csr_cmd
      //   |  |  |      |  |     |  |     |     |  |      |  +-- lsu_cmd
      //   |  |  |      |  |     |  |     |     |  |      +----- lsu
      //   |  |  |      |  |     |  |     |     |  +------------ alu_fn
      //   |  |  |      |  |     |  |     |     +--------------- alu_mode
      //   |  |  |      |  |     |  |     +-------------------- alu_sel2
      //   |  |  |      |  |     |  +-------------------------- alu_sel1
      //   |  |  |      |  |     +----------------------------- alu
      //   |  |  |      |  +------------------------------------ brFn
      //   |  |  |      +------------------------------------ branch
      //   |  |  +---------------------------------- imm_type
      //   |  +------------------------------------- regwrite
      //   +---------------------------------------- legal
    override def bubble: BitPat        = RV32I.NOP

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

    override def table: Array[(BitPat, List[BitPat])] =
      Array(
        // R-Type
        // Arithmetic
        RV32I.ADD  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_ADD, N, M_X, N, C_X),
        RV32I.SUB  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, Y, AFN_ADD, N, M_X, N, C_X),
        RV32I.SLL  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_SLL, N, M_X, N, C_X),
        RV32I.SLT  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_SLT, N, M_X, N, C_X),
        RV32I.SLTU -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_SLTU, N, M_X, N, C_X),
        RV32I.XOR  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_XOR, N, M_X, N, C_X),
        RV32I.SRL  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_SRL, N, M_X, N, C_X),
        RV32I.SRA  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, Y, AFN_SRL, N, M_X, N, C_X),
        RV32I.OR   -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_OR, N, M_X, N, C_X),
        RV32I.AND  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_AND, N, M_X, N, C_X),

        // I-Type
        // Arithmetic
        RV32I.ADDI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
        RV32I.SLLI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_SLL, N, M_X, N, C_X),
        RV32I.SLTI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_SLT, N, M_X, N, C_X),
        RV32I.SLTIU -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_SLTU, N, M_X, N, C_X),
        RV32I.XORI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_XOR, N, M_X, N, C_X),
        RV32I.SRLI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_SRL, N, M_X, N, C_X),
        RV32I.SRAI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, Y, AFN_SRL, N, M_X, N, C_X),
        RV32I.ORI   -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_OR, N, M_X, N, C_X),
        RV32I.ANDI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_AND, N, M_X, N, C_X),

        // Load
        RV32I.LB  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LB, N, C_X),
        RV32I.LH  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LH, N, C_X),
        RV32I.LW  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LW, N, C_X),
        RV32I.LBU -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LBU, N, C_X),
        RV32I.LHU -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LHU, N, C_X),

        // Jump
        RV32I.JALR -> List(Y, Y, IMM_I, Y, BR_JALR, Y, A1_PC, A2_FOUR, N, AFN_ADD, N, M_X, N, C_X),

        // CSR
        RV32I.CSRRW  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_X, N, M_X, Y, C_RW),
        RV32I.CSRRS  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_X, N, M_X, Y, C_RS),
        RV32I.CSRRC  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_X, N, M_X, Y, C_RC),
        RV32I.CSRRWI -> List(Y, Y, IMM_I, N, BR_X, Y, A1_ZERO, A2_IMM, N, AFN_X, N, M_X, Y, C_RW),
        RV32I.CSRRSI -> List(Y, Y, IMM_I, N, BR_X, Y, A1_ZERO, A2_IMM, N, AFN_X, N, M_X, Y, C_RS),
        RV32I.CSRRCI -> List(Y, Y, IMM_I, N, BR_X, Y, A1_ZERO, A2_IMM, N, AFN_X, N, M_X, Y, C_RC),

        // S-Type
        // Store
        RV32I.SB -> List(Y, N, IMM_S, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_SB, N, C_X),
        RV32I.SH -> List(Y, N, IMM_S, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_SH, N, C_X),
        RV32I.SW -> List(Y, N, IMM_S, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_SW, N, C_X),

        // B-Type
        // Branch
        RV32I.BEQ  -> List(Y, N, IMM_B, Y, BR_EQ, N, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
        RV32I.BNE  -> List(Y, N, IMM_B, Y, BR_NE, N, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
        RV32I.BLT  -> List(Y, N, IMM_B, Y, BR_LT, N, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
        RV32I.BGE  -> List(Y, N, IMM_B, Y, BR_GE, N, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
        RV32I.BLTU -> List(Y, N, IMM_B, Y, BR_LTU, N, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
        RV32I.BGEU -> List(Y, N, IMM_B, Y, BR_GEU, N, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),

        // U-Type
        // Upper Immediate
        RV32I.LUI   -> List(Y, Y, IMM_U, N, BR_X, Y, A1_ZERO, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
        RV32I.AUIPC -> List(Y, Y, IMM_U, N, BR_X, Y, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),

        // J-Type
        // Jump and Link
        RV32I.JAL -> List(Y, Y, IMM_J, Y, BR_JAL, Y, A1_PC, A2_FOUR, N, AFN_ADD, N, M_X, N, C_X)
      )
  }

  override def factory: UtilitiesFactory[DecoderUtilities] = DecoderUtilitiesFactory
}
