package core.common

import chisel3._

object OpCode {
  val LOAD     = "b0000011".U(7.W)
  val MISC_MEM = "b0001111".U(7.W)
  val OP_IMM   = "b0010011".U(7.W)
  val AUIPC    = "b0010111".U(7.W)
  val STORE    = "b0100011".U(7.W)
  val OP       = "b0110011".U(7.W)
  val LUI      = "b0110111".U(7.W)
  val BRANCH   = "b1100011".U(7.W)
  val JALR     = "b1100111".U(7.W)
  val JAL      = "b1101111".U(7.W)
  val SYSTEM   = "b1110011".U(7.W)
}

object ALUOp extends ChiselEnum {
  val ADD  = "b000".U(3.W)
  val SLL  = "b001".U(3.W)
  val SLT  = "b010".U(3.W)
  val SLTU = "b011".U(3.W)
  val XOR  = "b100".U(3.W)
  val SRL  = "b101".U(3.W)
  val OR   = "b110".U(3.W)
  val AND  = "b111".U(3.W)
}

object BranchOp extends ChiselEnum {
  val BEQ  = "b000".U(3.W)
  val BNE  = "b001".U(3.W)
  val BLT  = "b100".U(3.W)
  val BGE  = "b101".U(3.W)
  val BLTU = "b110".U(3.W)
  val BGEU = "b111".U(3.W)
}

object LoadOp extends ChiselEnum {
  val LB  = "b000".U(3.W)
  val LH  = "b001".U(3.W)
  val LW  = "b010".U(3.W)
  val LBU = "b100".U(3.W)
  val LHU = "b101".U(3.W)
}

object StoreOp extends ChiselEnum {
  val SB = "b000".U(3.W)
  val SH = "b001".U(3.W)
  val SW = "b010".U(3.W)
}
