package arch.core.decoder

import arch.isa.RV32I
import chisel3._
import chisel3.util._

class RV32ICtrlSigs extends Bundle with RV32IDecodeConsts {
  val legal = Bool()

  // imm
  val imm_sel = UInt(SZ_IMM.W)

  // alu
  val alu      = Bool()
  val alu_sel1 = UInt(SZ_A1.W)
  val alu_sel2 = UInt(SZ_A2.W)
  val alu_mode = Bool()
  val alu_fn   = UInt(SZ_AFN.W)

  // lsu
  val lsu     = Bool()
  val lsu_cmd = UInt(SZ_M.W)
}

class RV32IDecoderUtilitiesImpl extends DecoderUtilities with RV32IDecodeConsts {
  def default: List[BitPat] =
    List(N, IMM_X, X, A1_X, A2_X, X, AFN_X, X, M_X)

  def createBundle(): Bundle = new RV32ICtrlSigs

  def decode(instr: UInt, table: Iterable[(BitPat, List[BitPat])]): Bundle = {
    val sigs    = Wire(new RV32ICtrlSigs)
    val decoder = DecodeLogic(instr, default, table)

    sigs.legal    := decoder(0).asBool
    sigs.imm_sel  := decoder(1)
    sigs.alu      := decoder(2).asBool
    sigs.alu_sel1 := decoder(3)
    sigs.alu_sel2 := decoder(4)
    sigs.alu_mode := decoder(5).asBool
    sigs.alu_fn   := decoder(6)
    sigs.lsu      := decoder(7).asBool
    sigs.lsu_cmd  := decoder(8)

    sigs
  }

  def table: Array[(BitPat, List[BitPat])] =
    Array(
      // R-Type
      // Arithmetic
      RV32I.ADD  -> List(Y, IMM_X, Y, A1_RS1, A2_RS2, N, AFN_ADD, N, M_X),
      RV32I.SUB  -> List(Y, IMM_X, Y, A1_RS1, A2_RS2, Y, AFN_ADD, N, M_X),
      RV32I.SLL  -> List(Y, IMM_X, Y, A1_RS1, A2_RS2, N, AFN_SLL, N, M_X),
      RV32I.SLT  -> List(Y, IMM_X, Y, A1_RS1, A2_RS2, N, AFN_SLT, N, M_X),
      RV32I.SLTU -> List(Y, IMM_X, Y, A1_RS1, A2_RS2, N, AFN_SLTU, N, M_X),
      RV32I.XOR  -> List(Y, IMM_X, Y, A1_RS1, A2_RS2, N, AFN_XOR, N, M_X),
      RV32I.SRL  -> List(Y, IMM_X, Y, A1_RS1, A2_RS2, N, AFN_SRL, N, M_X),
      RV32I.SRA  -> List(Y, IMM_X, Y, A1_RS1, A2_RS2, Y, AFN_SRL, N, M_X),
      RV32I.OR   -> List(Y, IMM_X, Y, A1_RS1, A2_RS2, N, AFN_OR, N, M_X),
      RV32I.AND  -> List(Y, IMM_X, Y, A1_RS1, A2_RS2, N, AFN_AND, N, M_X),

      // I-Type
      // Arithmetic
      RV32I.ADDI  -> List(Y, IMM_I, Y, A1_RS1, A2_IMM, N, AFN_ADD, N, M_X),
      RV32I.SLLI  -> List(Y, IMM_I, Y, A1_RS1, A2_IMM, N, AFN_SLL, N, M_X),
      RV32I.SLTI  -> List(Y, IMM_I, Y, A1_RS1, A2_IMM, N, AFN_SLT, N, M_X),
      RV32I.SLTIU -> List(Y, IMM_I, Y, A1_RS1, A2_IMM, N, AFN_SLTU, N, M_X),
      RV32I.XORI  -> List(Y, IMM_I, Y, A1_RS1, A2_IMM, N, AFN_XOR, N, M_X),
      RV32I.SRLI  -> List(Y, IMM_I, Y, A1_RS1, A2_IMM, N, AFN_SRL, N, M_X),
      RV32I.SRAI  -> List(Y, IMM_I, Y, A1_RS1, A2_IMM, Y, AFN_SRL, N, M_X),
      RV32I.ORI   -> List(Y, IMM_I, Y, A1_RS1, A2_IMM, N, AFN_OR, N, M_X),
      RV32I.ANDI  -> List(Y, IMM_I, Y, A1_RS1, A2_IMM, N, AFN_AND, N, M_X),

      // Load
      RV32I.LB  -> List(Y, IMM_I, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LB),
      RV32I.LH  -> List(Y, IMM_I, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LH),
      RV32I.LW  -> List(Y, IMM_I, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LW),
      RV32I.LBU -> List(Y, IMM_I, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LBU),
      RV32I.LHU -> List(Y, IMM_I, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_LHU),

      // Jump
      RV32I.JALR -> List(Y, IMM_I, Y, A1_PC, A2_IMM, N, AFN_ADD, N, M_X),

      // S-Type
      // Store
      RV32I.SB -> List(Y, IMM_S, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_SB),
      RV32I.SH -> List(Y, IMM_S, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_SH),
      RV32I.SW -> List(Y, IMM_S, Y, A1_RS1, A2_IMM, N, AFN_ADD, Y, M_SW),

      // B-Type
      // Branch
      RV32I.BEQ  -> List(Y, IMM_B, Y, A1_PC, A2_IMM, N, AFN_ADD, N, M_X),
      RV32I.BNE  -> List(Y, IMM_B, Y, A1_PC, A2_IMM, N, AFN_ADD, N, M_X),
      RV32I.BLT  -> List(Y, IMM_B, Y, A1_PC, A2_IMM, N, AFN_SLT, N, M_X),
      RV32I.BGE  -> List(Y, IMM_B, Y, A1_PC, A2_IMM, N, AFN_SLT, N, M_X),
      RV32I.BLTU -> List(Y, IMM_B, Y, A1_PC, A2_IMM, N, AFN_SLTU, N, M_X),
      RV32I.BGEU -> List(Y, IMM_B, Y, A1_PC, A2_IMM, N, AFN_SLTU, N, M_X),

      // U-Type
      // Upper Immediate
      RV32I.LUI   -> List(Y, IMM_U, Y, A1_ZERO, A2_IMM, N, AFN_ADD, N, M_X),
      RV32I.AUIPC -> List(Y, IMM_U, Y, A1_PC, A2_IMM, N, AFN_ADD, N, M_X),

      // J-Type
      // Jump and Link
      RV32I.JAL -> List(Y, IMM_J, Y, A1_PC, A2_IMM, N, AFN_ADD, N, M_X)
    )

}

object RV32IDecoderUtilities extends RegisteredDecoderUtilities with RV32IDecodeConsts {
  override def isaName: String         = "rv32i"
  override def utils: DecoderUtilities = new RV32IDecoderUtilitiesImpl
}
