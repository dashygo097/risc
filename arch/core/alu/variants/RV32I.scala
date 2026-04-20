package arch.core.alu.riscv

import arch.core.alu._
import arch.configs._
import chisel3._
import chisel3.util.{ BitPat, MuxLookup }

// Format: uop[7:6] = sel1 | uop[5:4] = sel2 | uop[3] = mode | uop[2:0] = fn
trait RV32IAluUopConsts extends AluConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)

  def AM_X  = BitPat("b?")
  def SZ_AM = AM_X.getWidth
  def AM_0  = BitPat("b0")
  def AM_1  = BitPat("b1")

  def AFN_X    = BitPat("b???")
  def SZ_AFN   = AFN_X.getWidth
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

  // U-Type
  def UOP_LUI   = cat(A1_ZERO, A2_IMM, AM_0, AFN_ADD)
  def UOP_AUIPC = cat(A1_PC, A2_IMM, AM_0, AFN_ADD)
}

object RV32IAluUtils extends RegisteredUtils[AluUtils] with RV32IAluUopConsts {
  override def utils: AluUtils = new AluUtils {
    override def name: String = "rv32i"

    override def sel1Width: Int   = SZ_A1
    override def sel2Width: Int   = SZ_A2
    override def fnTypeWidth: Int = SZ_AFN

    override def decode(uop: UInt): AluCtrl = {
      val ctrl = Wire(new AluCtrl(fnTypeWidth))
      ctrl.sel1 := uop(7, 6)
      ctrl.sel2 := uop(5, 4)
      ctrl.mode := uop(3)
      ctrl.fn   := uop(2, 0)
      ctrl
    }

    override def fn(src1: UInt, src2: UInt, fnType: UInt, mode: Bool): UInt = {
      val lt  = src1.asSInt < src2.asSInt
      val ltu = src1 < src2

      val src2_inv  = Mux(mode, ~src2, src2)
      val adder_out = (src1 + src2_inv + mode.asUInt)(p(XLen) - 1, 0)

      val shamt   = src2(4, 0)
      val sll_out = (src1 << shamt)(p(XLen) - 1, 0)
      val srl_out = Mux(
        mode,
        (src1.asSInt >> shamt).asUInt,
        src1 >> shamt
      )(p(XLen) - 1, 0)

      val arith_result = MuxLookup(fnType, 0.U(p(XLen).W))(
        Seq(
          AFN_ADD.value.U(SZ_AFN.W)  -> adder_out,
          AFN_SLL.value.U(SZ_AFN.W)  -> sll_out,
          AFN_SLT.value.U(SZ_AFN.W)  -> lt.asUInt,
          AFN_SLTU.value.U(SZ_AFN.W) -> ltu.asUInt,
          AFN_XOR.value.U(SZ_AFN.W)  -> (src1 ^ src2),
          AFN_SRL.value.U(SZ_AFN.W)  -> srl_out,
          AFN_OR.value.U(SZ_AFN.W)   -> (src1 | src2),
          AFN_AND.value.U(SZ_AFN.W)  -> (src1 & src2)
        )
      )

      arith_result
    }
  }

  override def factory: UtilsFactory[AluUtils] = AluUtilsFactory
}
