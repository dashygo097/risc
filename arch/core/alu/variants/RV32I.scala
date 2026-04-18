package arch.core.alu

import arch.configs._
import chisel3._
import chisel3.util.{ BitPat, MuxLookup }

// Format: uop[7:6] = sel1 | uop[5:4] = sel2 | uop[3] = mode | uop[2:0] = fn
trait RV32IAluUopConsts extends AluConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)

  def AX_X = BitPat("b?")
  def AM_0 = BitPat("b0")
  def AM_1 = BitPat("b1")

  def AFN_X    = BitPat("b???")
  def AFN_ADD  = BitPat("b000")
  def AFN_SLL  = BitPat("b001")
  def AFN_SLT  = BitPat("b010")
  def AFN_SLTU = BitPat("b011")
  def AFN_XOR  = BitPat("b100")
  def AFN_SRL  = BitPat("b101")
  def AFN_OR   = BitPat("b110")
  def AFN_AND  = BitPat("b111")

  // R-Type
  def UOP_ADD  = cat(A1_RS1, A2_RS2, AM_0, AFN_ADD)
  def UOP_SUB  = cat(A1_RS1, A2_RS2, AM_1, AFN_ADD)
  def UOP_SLL  = cat(A1_RS1, A2_RS2, AM_0, AFN_SLL)
  def UOP_SLT  = cat(A1_RS1, A2_RS2, AM_0, AFN_SLT)
  def UOP_SLTU = cat(A1_RS1, A2_RS2, AM_0, AFN_SLTU)
  def UOP_XOR  = cat(A1_RS1, A2_RS2, AM_0, AFN_XOR)
  def UOP_SRL  = cat(A1_RS1, A2_RS2, AM_0, AFN_SRL)
  def UOP_SRA  = cat(A1_RS1, A2_RS2, AM_1, AFN_SRL)
  def UOP_OR   = cat(A1_RS1, A2_RS2, AM_0, AFN_OR)
  def UOP_AND  = cat(A1_RS1, A2_RS2, AM_0, AFN_AND)

  // I-Type
  def UOP_ADDI  = cat(A1_RS1, A2_IMM, AM_0, AFN_ADD)
  def UOP_SLLI  = cat(A1_RS1, A2_IMM, AM_0, AFN_SLL)
  def UOP_SLTI  = cat(A1_RS1, A2_IMM, AM_0, AFN_SLT)
  def UOP_SLTIU = cat(A1_RS1, A2_IMM, AM_0, AFN_SLTU)
  def UOP_XORI  = cat(A1_RS1, A2_IMM, AM_0, AFN_XOR)
  def UOP_SRLI  = cat(A1_RS1, A2_IMM, AM_0, AFN_SRL)
  def UOP_SRAI  = cat(A1_RS1, A2_IMM, AM_1, AFN_SRL)
  def UOP_ORI   = cat(A1_RS1, A2_IMM, AM_0, AFN_OR)
  def UOP_ANDI  = cat(A1_RS1, A2_IMM, AM_0, AFN_AND)

  // Jumps / U-Type
  def UOP_JALR  = cat(A1_PC, A2_PCSTEP, AM_0, AFN_ADD)
  def UOP_JAL   = cat(A1_PC, A2_PCSTEP, AM_0, AFN_ADD)
  def UOP_LUI   = cat(A1_ZERO, A2_IMM, AM_0, AFN_ADD)
  def UOP_AUIPC = cat(A1_PC, A2_IMM, AM_0, AFN_ADD)
}

object RV32IAluUtilities extends RegisteredUtilities[AluUtilities] with RV32IAluUopConsts {
  override def utils: AluUtilities = new AluUtilities {
    override def name: String = "rv32i"

    override def sel1Width: Int   = SZ_A1
    override def sel2Width: Int   = SZ_A2
    override def fnTypeWidth: Int = 3

    override def decode(uop: UInt): AluCtrl = {
      val ctrl = Wire(new AluCtrl(fnTypeWidth))
      ctrl.sel1 := uop(7, 6)
      ctrl.sel2 := uop(5, 4)
      ctrl.mode := uop(3)
      ctrl.fn   := uop(2, 0)
      ctrl
    }

    override def fn(src1: UInt, src2: UInt, fnType: UInt, mode: Bool): UInt = {
      val src2_inv = Mux(mode, ~src2, src2)
      val sum_res  = src1 +& src2_inv + mode.asUInt

      val sum   = sum_res(p(XLen) - 1, 0)
      val carry = sum_res(p(XLen))

      val sign1 = src1(p(XLen) - 1)
      val sign2 = src2(p(XLen) - 1)

      val lt  = Mux(sign1 === sign2, sum(p(XLen) - 1), sign1)
      val ltu = !carry

      val shamt   = src2(4, 0)
      val sll_out = (src1 << shamt)(p(XLen) - 1, 0)
      val srl_out = Mux(
        mode,
        (src1.asSInt >> shamt).asUInt,
        src1 >> shamt
      )(p(XLen) - 1, 0)

      MuxLookup(fnType, 0.U(p(XLen).W))(
        Seq(
          AFN_ADD.value.U  -> sum,
          AFN_SLL.value.U  -> sll_out,
          AFN_SLT.value.U  -> lt.asUInt,
          AFN_SLTU.value.U -> ltu.asUInt,
          AFN_XOR.value.U  -> (src1 ^ src2),
          AFN_SRL.value.U  -> srl_out,
          AFN_OR.value.U   -> (src1 | src2),
          AFN_AND.value.U  -> (src1 & src2)
        )
      )
    }
  }

  override def factory: UtilitiesFactory[AluUtilities] = AluUtilitiesFactory
}
