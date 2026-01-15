package arch.core.decoder

import arch.core.common.Consts
import arch.core.imm.RV32IImmConsts
import arch.core.alu.RV32IAluConsts
import arch.core.lsu.RV32ILsuConsts
import arch.core.bru.RV32IBranchConsts
import arch.core.csr.RV32ICsrConsts
import arch.configs._
import arch.isa._
import chisel3._
import chisel3.util._

trait RV32IDecodeConsts extends Consts with RV32IAluConsts with RV32ILsuConsts with RV32IImmConsts with RV32IBranchConsts with RV32ICsrConsts

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
    override def bubble: BitPat        = RV32IInstructionSet.NOP

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
        RV32IInstructionSet.ADD  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_ADD, N, M_X, N, C_X),
        RV32IInstructionSet.SUB  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, Y, AFN_ADD, N, M_X, N, C_X),
        RV32IInstructionSet.SLL  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_SLL, N, M_X, N, C_X),
        RV32IInstructionSet.SLT  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_SLT, N, M_X, N, C_X),
        RV32IInstructionSet.SLTU -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_SLTU, N, M_X, N, C_X),
        RV32IInstructionSet.XOR  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_XOR, N, M_X, N, C_X),
        RV32IInstructionSet.SRL  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_SRL, N, M_X, N, C_X),
        RV32IInstructionSet.SRA  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, Y, AFN_SRL, N, M_X, N, C_X),
        RV32IInstructionSet.OR   -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_OR, N, M_X, N, C_X),
        RV32IInstructionSet.AND  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_AND, N, M_X, N, C_X),

        // I-Type
        // Arithmetic
        RV32IInstructionSet.ADDI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
        RV32IInstructionSet.SLLI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_SLL, N, M_X, N, C_X),
        RV32IInstructionSet.SLTI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_SLT, N, M_X, N, C_X),
        RV32IInstructionSet.SLTIU -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_SLTU, N, M_X, N, C_X),
        RV32IInstructionSet.XORI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_XOR, N, M_X, N, C_X),
        RV32IInstructionSet.SRLI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_SRL, N, M_X, N, C_X),
        RV32IInstructionSet.SRAI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, Y, AFN_SRL, N, M_X, N, C_X),
        RV32IInstructionSet.ORI   -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_OR, N, M_X, N, C_X),
        RV32IInstructionSet.ANDI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_AND, N, M_X, N, C_X),

        // Load
        RV32IInstructionSet.LB  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LB, N, C_X),
        RV32IInstructionSet.LH  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LH, N, C_X),
        RV32IInstructionSet.LW  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LW, N, C_X),
        RV32IInstructionSet.LBU -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LBU, N, C_X),
        RV32IInstructionSet.LHU -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LHU, N, C_X),

        // Jump
        RV32IInstructionSet.JALR -> List(Y, Y, IMM_I, Y, BR_JALR, Y, A1_PC, A2_FOUR, N, AFN_ADD, N, M_X, N, C_X),

        // CSR
        RV32IInstructionSet.CSRRW  -> List(Y, Y, IMM_I, N, BR_X, N, A1_RS1, A2_IMM, N, AFN_X, N, M_X, Y, C_RW),
        RV32IInstructionSet.CSRRS  -> List(Y, Y, IMM_I, N, BR_X, N, A1_RS1, A2_IMM, N, AFN_X, N, M_X, Y, C_RS),
        RV32IInstructionSet.CSRRC  -> List(Y, Y, IMM_I, N, BR_X, N, A1_RS1, A2_IMM, N, AFN_X, N, M_X, Y, C_RC),
        RV32IInstructionSet.CSRRWI -> List(Y, Y, IMM_I, N, BR_X, N, A1_ZERO, A2_IMM, N, AFN_X, N, M_X, Y, C_RWI),
        RV32IInstructionSet.CSRRSI -> List(Y, Y, IMM_I, N, BR_X, N, A1_ZERO, A2_IMM, N, AFN_X, N, M_X, Y, C_RSI),
        RV32IInstructionSet.CSRRCI -> List(Y, Y, IMM_I, N, BR_X, N, A1_ZERO, A2_IMM, N, AFN_X, N, M_X, Y, C_RCI),

        // S-Type
        // Store
        RV32IInstructionSet.SB -> List(Y, N, IMM_S, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_SB, N, C_X),
        RV32IInstructionSet.SH -> List(Y, N, IMM_S, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_SH, N, C_X),
        RV32IInstructionSet.SW -> List(Y, N, IMM_S, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_SW, N, C_X),

        // B-Type
        // Branch
        RV32IInstructionSet.BEQ  -> List(Y, N, IMM_B, Y, BR_EQ, N, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
        RV32IInstructionSet.BNE  -> List(Y, N, IMM_B, Y, BR_NE, N, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
        RV32IInstructionSet.BLT  -> List(Y, N, IMM_B, Y, BR_LT, N, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
        RV32IInstructionSet.BGE  -> List(Y, N, IMM_B, Y, BR_GE, N, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
        RV32IInstructionSet.BLTU -> List(Y, N, IMM_B, Y, BR_LTU, N, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
        RV32IInstructionSet.BGEU -> List(Y, N, IMM_B, Y, BR_GEU, N, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),

        // U-Type
        // Upper Immediate
        RV32IInstructionSet.LUI   -> List(Y, Y, IMM_U, N, BR_X, Y, A1_ZERO, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),
        RV32IInstructionSet.AUIPC -> List(Y, Y, IMM_U, N, BR_X, Y, A1_PC, A2_IMM, N, AFN_ADD, N, M_X, N, C_X),

        // J-Type
        // Jump and Link
        RV32IInstructionSet.JAL -> List(Y, Y, IMM_J, Y, BR_JAL, Y, A1_PC, A2_FOUR, N, AFN_ADD, N, M_X, N, C_X)
      )
  }

  override def factory: UtilitiesFactory[DecoderUtilities] = DecoderUtilitiesFactory
}
