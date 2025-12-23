package core.ex

import core.common._
import chisel3._
import chisel3.util._

class RV32ALU extends Module {
  override def desiredName: String = s"rv32_alu"

  val rs1_data = IO(Input(UInt(32.W))).suggestName("ALU_RS1_DATA")
  val rs2_data = IO(Input(UInt(32.W))).suggestName("ALU_RS2_DATA")
  val result   = IO(Output(UInt(32.W))).suggestName("ALU_RESULT")

  // control signals
  val alu_is_sub = IO(Input(Bool())).suggestName("ALU_IS_SUB")
  val alu_is_sra = IO(Input(Bool())).suggestName("ALU_IS_SRA")
  val alu_op     = IO(Input(UInt(3.W))).suggestName("ALU_OP")

  result := MuxLookup(alu_op, 0.U)(
    Seq(
      ALUOp.ADD  -> Mux(alu_is_sub, rs1_data - rs2_data, rs1_data + rs2_data),
      ALUOp.SLL  -> (rs1_data << rs2_data(4, 0)),
      ALUOp.SLT  -> Mux(rs1_data.asSInt < rs2_data.asSInt, 1.U, 0.U),
      ALUOp.SLTU -> Mux(rs1_data < rs2_data, 1.U, 0.U),
      ALUOp.XOR  -> (rs1_data ^ rs2_data),
      ALUOp.SRL  -> Mux(
        alu_is_sra,
        (rs1_data.asSInt >> rs2_data(4, 0)).asUInt,
        rs1_data >> rs2_data(4, 0)
      ),
      ALUOp.OR   -> (rs1_data | rs2_data),
      ALUOp.AND  -> (rs1_data & rs2_data),
    )
  )
}
