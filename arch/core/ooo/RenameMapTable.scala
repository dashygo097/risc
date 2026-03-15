package arch.core.rename

import arch.configs._
import chisel3._
import chisel3.util._

class RenameMapTable(implicit p: Parameters) extends Module {
  // RS1, RS2
  val read_rs1 = IO(Input(UInt(log2Ceil(p(NumArchRegs)).W)))
  val read_rs2 = IO(Input(UInt(log2Ceil(p(NumArchRegs)).W)))
  val rs1_preg = IO(Output(UInt(log2Ceil(p(NumPhyRegs)).W)))
  val rs2_preg = IO(Output(UInt(log2Ceil(p(NumPhyRegs)).W)))

  // RD allocation
  val alloc_en     = IO(Input(Bool()))
  val arch_reg     = IO(Input(UInt(log2Ceil(p(NumArchRegs)).W)))
  val phys_reg     = IO(Input(UInt(log2Ceil(p(NumPhyRegs)).W)))
  val old_phys_reg = IO(Output(UInt(log2Ceil(p(NumPhyRegs)).W)))

  val map_table = RegInit(VecInit(Seq.fill(p(NumArchRegs))(0.U(log2Ceil(p(NumPhyRegs)).W))))

  rs1_preg := map_table(read_rs1)
  rs2_preg := map_table(read_rs2)

  old_phys_reg := map_table(arch_reg)

  when(alloc_en && arch_reg =/= 0.U) {
    map_table(arch_reg) := phys_reg
  }
}
