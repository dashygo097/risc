package arch.core.decoder

import arch.isa.RV32I
import arch.core.common.Consts
import arch.core.imm.RV32ImmConsts
import arch.core.alu.RV32IAluConsts
import arch.core.lsu.RV32ILsuConsts
import arch.core.regfile.RV32IRegfileConsts
import arch.core.bru.RV32IBranchConsts
import chisel3._
import chisel3.util._

trait RV32IDecodeConsts extends Consts with RV32IAluConsts with RV32ILsuConsts with RV32ImmConsts with RV32IRegfileConsts with RV32IBranchConsts

class RV32IDecoderUtilitiesImpl extends DecoderUtilities with RV32IDecodeConsts {
  def default: List[BitPat] =
    List(N, N, IMM_X, X, BR_X, X, A1_X, A2_X, X, AFN_X, X, M_X)
    //   ^  ^  ^      ^  |     ^  ^     ^     ^  ^      ^  ^
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
  def bubble: BitPat        = RV32I.NOP

  def decode(instr: UInt): DecodedOutput = {
    val sigs    = Wire(new DecodedOutput)
    val decoder = DecodeLogic(instr, default, table)

    sigs.legal    := decoder(0).asBool
    sigs.regwrite := decoder(1).asBool
    sigs.imm_type := decoder(2)
    sigs.branch   := decoder(3).asBool
    sigs.brFn     := decoder(4)
    sigs.alu      := decoder(5).asBool
    sigs.alu_sel1 := decoder(6)
    sigs.alu_sel2 := decoder(7)
    sigs.alu_mode := decoder(8).asBool
    sigs.alu_fn   := decoder(9)
    sigs.lsu      := decoder(10).asBool
    sigs.lsu_cmd  := decoder(11)

    sigs
  }

  def table: Array[(BitPat, List[BitPat])] =
    Array(
      // R-Type
      // Arithmetic
      RV32I.ADD  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_ADD, N, M_X),
      RV32I.SUB  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, Y, AFN_ADD, N, M_X),
      RV32I.SLL  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_SLL, N, M_X),
      RV32I.SLT  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_SLT, N, M_X),
      RV32I.SLTU -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_SLTU, N, M_X),
      RV32I.XOR  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_XOR, N, M_X),
      RV32I.SRL  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_SRL, N, M_X),
      RV32I.SRA  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, Y, AFN_SRL, N, M_X),
      RV32I.OR   -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_OR, N, M_X),
      RV32I.AND  -> List(Y, Y, IMM_X, N, BR_X, Y, A1_RS1, A2_RS2, N, AFN_AND, N, M_X),

      // I-Type
      // Arithmetic
      RV32I.ADDI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, N, M_X),
      RV32I.SLLI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_SLL, N, M_X),
      RV32I.SLTI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_SLT, N, M_X),
      RV32I.SLTIU -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_SLTU, N, M_X),
      RV32I.XORI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_XOR, N, M_X),
      RV32I.SRLI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_SRL, N, M_X),
      RV32I.SRAI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, Y, AFN_SRL, N, M_X),
      RV32I.ORI   -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_OR, N, M_X),
      RV32I.ANDI  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_AND, N, M_X),

      // Load
      RV32I.LB  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LB),
      RV32I.LH  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LH),
      RV32I.LW  -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LW),
      RV32I.LBU -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LBU),
      RV32I.LHU -> List(Y, Y, IMM_I, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LHU),

      // Jump
      RV32I.JALR -> List(Y, N, IMM_I, N, BR_X, Y, A1_PC, A2_IMM, N, AFN_ADD, N, M_X),

      // S-Type
      // Store
      RV32I.SB -> List(Y, N, IMM_S, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_SB),
      RV32I.SH -> List(Y, N, IMM_S, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_SH),
      RV32I.SW -> List(Y, N, IMM_S, N, BR_X, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_SW),

      // B-Type
      // Branch
      RV32I.BEQ  -> List(Y, N, IMM_B, Y, BR_EQ, Y, A1_PC, A2_IMM, N, AFN_ADD, N, M_X),
      RV32I.BNE  -> List(Y, N, IMM_B, Y, BR_NE, Y, A1_PC, A2_IMM, N, AFN_ADD, N, M_X),
      RV32I.BLT  -> List(Y, N, IMM_B, Y, BR_LT, Y, A1_PC, A2_IMM, N, AFN_ADD, N, M_X),
      RV32I.BGE  -> List(Y, N, IMM_B, Y, BR_GE, Y, A1_PC, A2_IMM, N, AFN_ADD, N, M_X),
      RV32I.BLTU -> List(Y, N, IMM_B, Y, BR_LTU, Y, A1_PC, A2_IMM, N, AFN_ADD, N, M_X),
      RV32I.BGEU -> List(Y, N, IMM_B, Y, BR_GEU, Y, A1_PC, A2_IMM, N, AFN_ADD, N, M_X),

      // U-Type
      // Upper Immediate
      RV32I.LUI   -> List(Y, Y, IMM_U, N, BR_X, Y, A1_ZERO, A2_IMM, N, AFN_ADD, N, M_X),
      RV32I.AUIPC -> List(Y, Y, IMM_U, N, BR_X, Y, A1_PC, A2_IMM, N, AFN_ADD, N, M_X),

      // J-Type
      // Jump and Link
      RV32I.JAL -> List(Y, Y, IMM_J, N, BR_X, Y, A1_PC, A2_IMM, N, AFN_ADD, N, M_X)
    )

}

object RV32IDecoderUtilities extends RegisteredDecoderUtilities with RV32IDecodeConsts {
  override def isaName: String         = "rv32i"
  override def utils: DecoderUtilities = new RV32IDecoderUtilitiesImpl
}
