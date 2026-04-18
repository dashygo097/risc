package arch.core.bru

import arch.configs._
import chisel3._
import chisel3.util.{ BitPat, MuxLookup }

// Format: uop[7:5] = 0 | uop[4] = is_jump | uop[3] = is_jalr | uop[2:0] = op
trait RV32IBruUOpConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)
  private def P_X                       = BitPat("b???")

  def B_JMP_0 = BitPat("b0")
  def B_JMP_1 = BitPat("b1")

  def B_JLR_0 = BitPat("b0")
  def B_JLR_1 = BitPat("b1")

  def B_EQ  = BitPat("b000")
  def B_NE  = BitPat("b001")
  def B_LT  = BitPat("b010")
  def B_GE  = BitPat("b011")
  def B_LTU = BitPat("b100")
  def B_GEU = BitPat("b101")
  def B_AL  = BitPat("b111")

  def UOP_BEQ  = cat(P_X, B_JMP_0, B_JLR_0, B_EQ)
  def UOP_BNE  = cat(P_X, B_JMP_0, B_JLR_0, B_NE)
  def UOP_BLT  = cat(P_X, B_JMP_0, B_JLR_0, B_LT)
  def UOP_BGE  = cat(P_X, B_JMP_0, B_JLR_0, B_GE)
  def UOP_BLTU = cat(P_X, B_JMP_0, B_JLR_0, B_LTU)
  def UOP_BGEU = cat(P_X, B_JMP_0, B_JLR_0, B_GEU)

  def UOP_JAL  = cat(P_X, B_JMP_1, B_JLR_0, B_AL)
  def UOP_JALR = cat(P_X, B_JMP_1, B_JLR_1, B_AL)
}

object RV32IBruUtilities extends RegisteredUtilities[BruUtilities] with RV32IBruUOpConsts {
  override def utils: BruUtilities = new BruUtilities {
    override def name: String = "rv32i"

    override def opWidth: Int     = 3
    override def hasJump: Boolean = true
    override def hasJalr: Boolean = true

    override def decode(uop: UInt): BruCtrl = {
      val ctrl = Wire(new BruCtrl(opWidth))
      ctrl.is_jump := uop(4)
      ctrl.is_jalr := uop(3)
      ctrl.op      := uop(2, 0)
      ctrl
    }

    override def fn(src1: UInt, src2: UInt, op: UInt): Bool = {
      val sub_res = src1 +& ~src2 + 1.U
      val sum     = sub_res(p(XLen) - 1, 0)
      val carry   = sub_res(p(XLen))

      val eq = sum === 0.U
      val ne = !eq

      val sign1    = src1(p(XLen) - 1)
      val sign2    = src2(p(XLen) - 1)
      val sum_sign = sum(p(XLen) - 1)

      val lt  = Mux(sign1 === sign2, sum_sign, sign1)
      val ltu = !carry

      MuxLookup(op, false.B)(
        Seq(
          "b000".U -> eq,
          "b001".U -> ne,
          "b010".U -> lt,
          "b011".U -> !lt,
          "b100".U -> ltu,
          "b101".U -> !ltu,
          "b111".U -> true.B
        )
      )
    }
  }

  override def factory: UtilitiesFactory[BruUtilities] = BruUtilitiesFactory
}
