package arch.core.regfile

import arch.configs._
import vopts.mem.register.DualPortRegFile
import chisel3._
import chisel3.util._

class Regfile(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_regfile"

  val utils = RegfileUtilitiesFactory.getOrThrow(p(ISA))

  val rs1_addr   = IO(Input(UInt(log2Ceil(p(NumArchRegs)).W)))
  val rs2_addr   = IO(Input(UInt(log2Ceil(p(NumArchRegs)).W)))
  val write_addr = IO(Input(UInt(log2Ceil(p(NumArchRegs)).W)))
  val write_data = IO(Input(UInt(p(XLen).W)))
  val write_en   = IO(Input(Bool()))

  val rs1_data = IO(Output(UInt(p(XLen).W)))
  val rs2_data = IO(Output(UInt(p(XLen).W)))

  val dual_port_regfile = Module(
    new DualPortRegFile(
      p(NumArchRegs),
      p(XLen),
      utils.extraInfo,
      isBypass = p(IsRegfileUseBypass)
    )
  )

  dual_port_regfile.rs1_addr   := rs1_addr
  dual_port_regfile.rs2_addr   := rs2_addr
  dual_port_regfile.write_addr := write_addr
  dual_port_regfile.write_data := write_data
  dual_port_regfile.write_en   := write_en
  rs1_data                     := dual_port_regfile.rs1_data
  rs2_data                     := dual_port_regfile.rs2_data
}
