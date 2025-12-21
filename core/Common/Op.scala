package core.common

import chisel3._

object OpCode {
  val LOAD     = "b0000011".U(7.W)
  val LOAD_FP  = "b0000111".U(7.W)
  val MISC_MEM = "b0001111".U(7.W)
  val OP_IMM   = "b0010011".U(7.W)
  val AUIPC    = "b0010111".U(7.W)
  val STORE    = "b0100011".U(7.W)
  val STORE_FP = "b0100111".U(7.W)
  val OP       = "b0110011".U(7.W)
  val LUI      = "b0110111".U(7.W)
  val BRANCH   = "b1100011".U(7.W)
  val JALR     = "b1100111".U(7.W)
  val JAL      = "b1101111".U(7.W)
  val SYSTEM   = "b1110011".U(7.W)
}

object ALUOp extends ChiselEnum {
  val ADD  = 0.U(4.W)
  val SUB  = 1.U(4.W)
  val SLL  = 2.U(4.W)
  val SLT  = 3.U(4.W)
  val SLTU = 4.U(4.W)
  val XOR  = 5.U(4.W)
  val SRL  = 6.U(4.W)
  val SRA  = 7.U(4.W)
  val OR   = 8.U(4.W)
  val AND  = 9.U(4.W)
  val COPY = 10.U(4.W)
}

object BranchOp extends ChiselEnum {
  val BEQ  = 0.U(3.W)
  val BNE  = 1.U(3.W)
  val BLT  = 4.U(3.W)
  val BGE  = 5.U(3.W)
  val BLTU = 6.U(3.W)
  val BGEU = 7.U(3.W)
}

object MemOp extends ChiselEnum {
  val LB  = 0.U(3.W)
  val LH  = 1.U(3.W)
  val LW  = 2.U(3.W)
  val LBU = 3.U(3.W)
  val LHU = 4.U(3.W)
  val SB  = 5.U(3.W)
  val SH  = 6.U(3.W)
  val SW  = 7.U(3.W)
}
