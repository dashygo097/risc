package core.id

import core.common._
import chisel3._
import chisel3.util._

class RV32Funct3Decoder extends Module {
  override def desiredName: String = "rv32_funct3_decoder"

  val opcode = IO(Input(UInt(7.W))).suggestName("OPCODE")
  val funct3 = IO(Input(UInt(3.W))).suggestName("FUNCT3")
  val funct7 = IO(Input(UInt(7.W))).suggestName("FUNCT7")

  val alu_op_imm = IO(Output(UInt(4.W))).suggestName("ALU_OP_IMM")
  val branch_op  = IO(Output(UInt(3.W))).suggestName("BRANCH_OP")
  val mem_op     = IO(Output(UInt(3.W))).suggestName("MEM_OP")

  val mem_width    = IO(Output(UInt(2.W))).suggestName("MEM_WIDTH") // 0: byte, 1: half, 2: word
  val mem_sign_ext =
    IO(Output(Bool())).suggestName("MEM_SING_EXT") // 0: zero extend, 1: sign extend

  // I-type ALU
  alu_op_imm := funct3

  // Branch
  branch_op := funct3

  // Load/Store
  mem_op := MuxCase(
    MemOp.LW,
    Seq(
      (opcode === OpCode.LOAD)  -> MuxLookup(funct3, MemOp.LW)(
        Seq(
          "b000".U -> MemOp.LB,  // LB
          "b001".U -> MemOp.LH,  // LH
          "b010".U -> MemOp.LW,  // LW
          "b100".U -> MemOp.LBU, // LBU
          "b101".U -> MemOp.LHU  // LHU
        )
      ),
      (opcode === OpCode.STORE) -> MuxLookup(funct3, MemOp.SW)(
        Seq(
          "b000".U -> MemOp.SB, // SB
          "b001".U -> MemOp.SH, // SH
          "b010".U -> MemOp.SW  // SW
        )
      )
    )
  )

  // Memory width
  mem_width := funct3(1, 0)

  // Memory sign extension
  mem_sign_ext := !funct3(2)
}
