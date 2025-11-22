package core

import ex._
import mem._
import utils._
import chisel3._
import chisel3.util._

class RV32GloblCtrlUnit extends Module {
  override def desiredName: String =
    s"rv32_globl_ctrl_unit"
  val inst     = IO(Input(UInt(32.W)))
  val alu_ctrl = IO(Output(UInt(4.W)))
  val mem_ctrl = IO(Output(UInt(3.W)))

  val opcode = inst(6, 0)
  val funct3 = inst(14, 12)
  val funct7 = inst(31, 25)

  alu_ctrl := MuxLookup(opcode, "b0000".U)(
    Seq(
      "b0110011".U -> MuxLookup(Cat(funct7, funct3), "b0000".U)(
        Seq( // R-type
          "b0000000_000".U -> ALUOp.ADD,
          "b0100000_000".U -> ALUOp.SUB,
          "b0000000_100".U -> ALUOp.XOR,
          "b0000000_110".U -> ALUOp.OR,
          "b0000000_111".U -> ALUOp.AND,
          "b0000000_001".U -> ALUOp.SLL,
          "b0000000_101".U -> ALUOp.SRL,
          "b0100000_101".U -> ALUOp.SRA,
          "b0000000_010".U -> ALUOp.SLT,
          "b0000000_011".U -> ALUOp.SLTU
        )
      ),
      "b0010011".U -> MuxLookup(funct3, "b0000".U)(
        Seq( // I-type
          "b000".U -> ALUOp.ADD,
          "b100".U -> ALUOp.XOR,
          "b110".U -> ALUOp.OR,
          "b111".U -> ALUOp.AND,
          "b001".U -> ALUOp.SLL,
          "b101".U -> Mux(funct7 === "b0000000".U, ALUOp.SRL, ALUOp.SRA),
          "b010".U -> ALUOp.SLT,
          "b011".U -> ALUOp.SLTU
        )
      ),
    )
  )

  mem_ctrl := MuxLookup(opcode, "b000".U)(
    Seq(
      "b0000011".U -> MuxLookup(funct3, "b000".U)(
        Seq( // Load, I-Type
          "b000".U -> MemOp.LB,
          "b001".U -> MemOp.LH,
          "b010".U -> MemOp.LW,
          "b100".U -> MemOp.LBU,
          "b101".U -> MemOp.LHU
        )
      ),
      "b0100011".U -> MuxLookup(funct3, "b000".U)(
        Seq( // Store, S-Type
          "b000".U -> MemOp.SB,
          "b001".U -> MemOp.SH,
          "b010".U -> MemOp.SW
        )
      )
    )
  )
}

object RV32GloblCtrlUnit extends App {
  VerilogEmitter.parse(new RV32GloblCtrlUnit, "rv32_globl_ctrl_unit.sv", info = true)
}
