package core.id

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
      (opcode === "b0010011".U) -> imm_i,
      (opcode === "b0000011".U) -> imm_i,
      (opcode === "b0100011".U) -> imm_s,
      (opcode === "b1100011".U) -> imm_b,
      (opcode === "b0110111".U) -> imm_u,
      (opcode === "b0010111".U) -> imm_u,
      (opcode === "b1101111".U) -> imm_j,
      (opcode === "b1100111".U) -> imm_i
    )
  )
}
