package arch.core.ooo

import arch.configs._
import chisel3._
import chisel3.util.log2Ceil

class InstructionStatus(implicit p: Parameters) extends Bundle {
  val issue        = Bool()
  val read_oper    = Bool()
  val exec_comp    = Bool()
  val write_result = Bool()
}

class FunctionalUnitStatus(implicit p: Parameters) extends Bundle {
  val busy      = Bool()
  val op        = UInt(p(ILen).W)
  val rd        = UInt(log2Ceil(p(NumArchRegs)).W)
  val rs1       = UInt(log2Ceil(p(NumArchRegs)).W)
  val rs2       = UInt(log2Ceil(p(NumArchRegs)).W)
  val rs1_prod  = UInt(4.W)
  val rs2_prod  = UInt(4.W)
  val rs1_ready = Bool()
  val rs2_ready = Bool()
}

class RegisterResultStatus(implicit p: Parameters) extends Bundle {
  val rs   = UInt(log2Ceil(p(NumArchRegs)).W)
  val prod = UInt(4.W)
}

object FUIds {
  val ALU     = 0
  val MUL     = 1
  val LSU     = 2
  val CSR     = 3
  val NUM_FUS = 4
}

class ScoreboardIO(implicit p: Parameters) extends Bundle {
  private val regWidth = log2Ceil(p(NumArchRegs))
  private val fuWidth  = log2Ceil(FUIds.NUM_FUS)

  val issue_valid = Input(Bool())
  val issue_instr = Input(UInt(p(ILen).W))
  val issue_rd    = Input(UInt(regWidth.W))
  val issue_rs1   = Input(UInt(regWidth.W))
  val issue_rs2   = Input(UInt(regWidth.W))
  val issue_fu_id = Input(UInt(fuWidth.W))

  val issue_ready = Output(Bool())

  val rs1_ready = Output(Bool())
  val rs2_ready = Output(Bool())

  val fu_done = Input(Vec(FUIds.NUM_FUS, Bool()))
  val fu_rd   = Input(Vec(FUIds.NUM_FUS, UInt(regWidth.W)))

  val fu_status    = Output(Vec(FUIds.NUM_FUS, new FunctionalUnitStatus))
  val reg_status   = Output(Vec(p(NumArchRegs), new RegisterResultStatus))
  val instr_status = Output(Vec(FUIds.NUM_FUS, new InstructionStatus))
}
