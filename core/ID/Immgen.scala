package core.id

import core.common._
import chisel3._
import chisel3.util._

class RV32ImmGen extends Module {
  override def desiredName: String = s"rv32_immgen"

  val inst = IO(Input(UInt(32.W))).suggestName("INST")
  val imm  = IO(Output(UInt(32.W))).suggestName("IMM")

  val opcode = inst(6, 0)

  // I-type immediate
  val imm_i = Cat(Fill(21, inst(31)), inst(30, 20))
  // S-type immediate
  val imm_s = Cat(Fill(21, inst(31)), inst(30, 25), inst(11, 7))
  // B-type immediate
  val imm_b = Cat(
    Fill(20, inst(31)),
    inst(7),
    inst(30, 25),
    inst(11, 8),
    0.U(1.W)
  )
  // U-type immediate
  val imm_u = Cat(inst(31, 12), 0.U(12.W))
  // J-type immediate
  val imm_j = Cat(
    Fill(12, inst(31)),
    inst(19, 12),
    inst(20),
    inst(30, 21),
    0.U(1.W)
  )

  imm := MuxCase(
    0.U,
    Seq(
      (opcode === OpCode.LOAD)     -> imm_i,
      (opcode === OpCode.MISC_MEM) -> imm_i,
      (opcode === OpCode.OP_IMM)   -> imm_i,
      (opcode === OpCode.JALR)     -> imm_i,
      (opcode === OpCode.SYSTEM)   -> imm_i,
      (opcode === OpCode.STORE)    -> imm_s,
      (opcode === OpCode.BRANCH)   -> imm_b,
      (opcode === OpCode.LUI)      -> imm_u,
      (opcode === OpCode.AUIPC)    -> imm_u,
      (opcode === OpCode.JAL)      -> imm_j
    )
  )
}
