package arch.core.decoder

import chisel3._
import chisel3.util.BitPat

// TODO: This decoded output only supports rv32i yet
class DecodedOutput extends Bundle with RV32IDecodeConsts {
  val legal = Bool()

  // imm
  val imm_sel = UInt(SZ_IMM.W)

  // regfile
  val regwrite = Bool()

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

trait DecoderUtilities {
  def default: List[BitPat]
  def createBundle(): Bundle
  def decode(instr: UInt): Bundle
  def table: Array[(BitPat, List[BitPat])]
}
