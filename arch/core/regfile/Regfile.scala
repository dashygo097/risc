package arch.core.regfile

import arch.configs._
import vopts.mem.register.MultiPortRegFile
import chisel3._
import chisel3.util.log2Ceil

class Regfile(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_regfile"

  val utils = RegfileUtilitiesFactory.getOrThrow(p(ISA).name)

  // NOTE: Renaming to be impled
  val rs1_preg   = IO(Input(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W))))
  val rs2_preg   = IO(Input(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W))))
  val write_preg = IO(Input(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W))))
  val write_data = IO(Input(Vec(p(IssueWidth), UInt(p(XLen).W))))
  val write_en   = IO(Input(Vec(p(IssueWidth), Bool())))

  val rs1_data = IO(Output(Vec(p(IssueWidth), UInt(p(XLen).W))))
  val rs2_data = IO(Output(Vec(p(IssueWidth), UInt(p(XLen).W))))

  val multi_port_regfile = Module(
    new MultiPortRegFile(
      p(NumArchRegs),
      p(XLen),
      p(IssueWidth),
      p(IssueWidth),
      utils.extraInfo,
      isBypass = p(IsRegfileUseBypass)
    )
  )

  multi_port_regfile.rs1_addr   := rs1_preg
  multi_port_regfile.rs2_addr   := rs2_preg
  multi_port_regfile.write_addr := write_preg
  multi_port_regfile.write_data := write_data
  multi_port_regfile.write_en   := write_en
  rs1_data                      := multi_port_regfile.rs1_data
  rs2_data                      := multi_port_regfile.rs2_data
}
