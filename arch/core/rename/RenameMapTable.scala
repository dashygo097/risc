package arch.core.rename

import arch.configs._
import chisel3._
import chisel3.util._

class RenameMapTable(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    // RS1, RS2
    val read_rs1 = Input(UInt(log2Ceil(p(NumArchRegs)).W))
    val read_rs2 = Input(UInt(log2Ceil(p(NumArchRegs)).W))
    val rs1_preg = Output(UInt(log2Ceil(p(NumPhyRegs)).W))
    val rs2_preg = Output(UInt(log2Ceil(p(NumPhyRegs)).W))

    // RD allocation
    val alloc_en     = Input(Bool())
    val arch_reg     = Input(UInt(log2Ceil(p(NumArchRegs)).W))
    val phys_reg     = Input(UInt(log2Ceil(p(NumPhyRegs)).W))
    val old_phys_reg = Output(UInt(log2Ceil(p(NumPhyRegs)).W))
  })

  val map_table = RegInit(VecInit(Seq.fill(p(NumArchRegs))(0.U(log2Ceil(p(NumPhyRegs)).W))))

  io.rs1_preg := map_table(io.read_rs1)
  io.rs2_preg := map_table(io.read_rs2)

  io.old_phys_reg := map_table(io.arch_reg)

  when(io.alloc_en && io.arch_reg =/= 0.U) {
    map_table(io.arch_reg) := io.phys_reg
  }
}
