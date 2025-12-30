package arch.core.decoder

import arch.configs._
import arch.core.bru._
import arch.core.imm._
import arch.core.alu._
import arch.core.lsu._
import arch.core.regfile._
import chisel3._
import chisel3.util.BitPat

class DecodedOutput(implicit p: Parameters) extends Bundle {
  val bru_utils     = BruUtilitiesFactory.getOrThrow(p(ISA))
  val imm_utils     = ImmUtilitiesFactory.getOrThrow(p(ISA))
  val alu_utils     = AluUtilitiesFactory.getOrThrow(p(ISA))
  val lsu_utils     = LsuUtilitiesFactory.getOrThrow(p(ISA))
  val regfile_utils = RegfileUtilitiesFactory.getOrThrow(p(ISA))

  val legal = Bool()

  // regfile
  val regwrite = Bool()

  // imm
  val imm_type = UInt(imm_utils.immTypeWidth.W)

  // branch
  val branch = Bool()
  val brFn   = UInt(bru_utils.branchTypeWidth.W)

  // alu
  val alu      = Bool()
  val alu_sel1 = UInt(alu_utils.sel1Width.W)
  val alu_sel2 = UInt(alu_utils.sel2Width.W)
  val alu_mode = Bool()
  val alu_fn   = UInt(alu_utils.fnTypeWidth.W)

  // lsu
  val lsu     = Bool()
  val lsu_cmd = UInt(lsu_utils.cmdWidth.W)
}

trait DecoderUtilities {
  def default: List[BitPat]
  def bubble: BitPat
  def decode(instr: UInt): DecodedOutput
  def table: Array[(BitPat, List[BitPat])]
}
