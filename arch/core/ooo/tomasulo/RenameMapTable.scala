package arch.core.ooo

import arch.configs._
import chisel3._
import chisel3.util.log2Ceil

class RenameMapTable(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_rename_map_table"
  val io                           = IO(new Bundle {
    // Read mappings for Dispatch
    val read_rs1 = Input(UInt(log2Ceil(p(NumArchRegs)).W))
    val read_rs2 = Input(UInt(log2Ceil(p(NumArchRegs)).W))
    val rs1_preg = Output(UInt(log2Ceil(p(NumPhyRegs)).W))
    val rs2_preg = Output(UInt(log2Ceil(p(NumPhyRegs)).W))

    // Speculative Rename
    val alloc_en     = Input(Bool())
    val arch_reg     = Input(UInt(log2Ceil(p(NumArchRegs)).W))
    val phys_reg     = Input(UInt(log2Ceil(p(NumPhyRegs)).W))
    val old_phys_reg = Output(UInt(log2Ceil(p(NumPhyRegs)).W))

    // Commit Rename
    val commit_en   = Input(Bool())
    val commit_arch = Input(UInt(log2Ceil(p(NumArchRegs)).W))
    val commit_phys = Input(UInt(log2Ceil(p(NumPhyRegs)).W))

    // Rollback
    val flush = Input(Bool())
  })

  val spec_table = RegInit(VecInit(Seq.tabulate(p(NumArchRegs))(i => i.U(log2Ceil(p(NumPhyRegs)).W))))
  val arch_table = RegInit(VecInit(Seq.tabulate(p(NumArchRegs))(i => i.U(log2Ceil(p(NumPhyRegs)).W))))

  io.rs1_preg     := spec_table(io.read_rs1)
  io.rs2_preg     := spec_table(io.read_rs2)
  io.old_phys_reg := spec_table(io.arch_reg)

  when(io.flush) {
    spec_table := arch_table
  }.otherwise {
    when(io.alloc_en && io.arch_reg =/= 0.U) {
      spec_table(io.arch_reg) := io.phys_reg
    }
    when(io.commit_en && io.commit_arch =/= 0.U) {
      arch_table(io.commit_arch) := io.commit_phys
    }
  }
}
