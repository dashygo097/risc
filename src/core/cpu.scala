package core

import chisel3._
import chisel3.util._
import utils._

class RV32CPU extends Module {
  override def desiredName: String = s"rv32_cpu"

  // External instruction memory interface
  val imem_addr = IO(Output(UInt(32.W)))
  val imem_inst = IO(Input(UInt(32.W)))
  
  // Debug outputs
  val debug_pc = IO(Output(UInt(32.W)))
  val debug_inst = IO(Output(UInt(32.W)))
  val debug_reg_write = IO(Output(Bool()))
  val debug_reg_addr = IO(Output(UInt(5.W)))
  val debug_reg_data = IO(Output(UInt(32.W)))

  // Modules
  val alu = Module(new RV32ALU)
  val ctrl_unit = Module(new RV32GloblCtrlUnit)
  val regfile = Module(new RV32RegFile)
  val data_mem = Module(new RV32GloblMem(32, 32, 1024, 0x80000000L))

  // Program Counter
  val pc = RegInit(0.U(32.W))
  val next_pc = Wire(UInt(32.W))

  // IF - Instruction Fetch
  imem_addr := pc
  val inst = imem_inst

  // ID - Instruction Decode
  val funct7 = inst(31, 25)
  val rs2 = inst(24, 20)
  val rs1 = inst(19, 15)
  val funct3 = inst(14, 12)
  val rd = inst(11, 7)
  val opcode = inst(6, 0)

  // Control signals
  ctrl_unit.inst := inst
  val alu_ctrl = ctrl_unit.alu_ctrl
  val mem_ctrl = ctrl_unit.mem_ctrl

  // Immediate generation
  val imm_i = Cat(Fill(21, inst(31)), inst(30, 20))
  val imm_s = Cat(Fill(21, inst(31)), inst(30, 25), inst(11, 7))
  val imm_b = Cat(Fill(20, inst(31)), inst(7), inst(30, 25), inst(11, 8), 0.U(1.W))
  val imm_u = Cat(inst(31, 12), Fill(12, 0.U))
  val imm_j = Cat(Fill(12, inst(31)), inst(19, 12), inst(20), inst(30, 21), 0.U(1.W))

  // Select immediate based on instruction type
  val imm = MuxCase(0.U, Seq(
    (opcode === "b0010011".U) -> imm_i,  // I-type (ALU)
    (opcode === "b0000011".U) -> imm_i,  // I-type (Load)
    (opcode === "b0100011".U) -> imm_s,  // S-type
    (opcode === "b1100011".U) -> imm_b,  // B-type
    (opcode === "b0110111".U) -> imm_u,  // U-type (LUI)
    (opcode === "b0010111".U) -> imm_u,  // U-type (AUIPC)
    (opcode === "b1101111".U) -> imm_j,  // J-type (JAL)
    (opcode === "b1100111".U) -> imm_i   // I-type (JALR)
  ))

  // Register file read
  regfile.rs1_addr := rs1
  regfile.rs2_addr := rs2
  val rs1_data = regfile.rs1_data
  val rs2_data = regfile.rs2_data

  // EX - Execute
  
  // ALU source selection
  val alu_src1 = MuxCase(rs1_data, Seq(
    (opcode === "b0010111".U) -> pc,  // AUIPC
    (opcode === "b1101111".U) -> pc,  // JAL
    (opcode === "b1100111".U) -> rs1_data  // JALR
  ))

  val alu_src2 = MuxCase(rs2_data, Seq(
    (opcode === "b0010011".U) -> imm,  // I-type ALU
    (opcode === "b0000011".U) -> imm,  // Load
    (opcode === "b0100011".U) -> imm,  // Store
    (opcode === "b0110111".U) -> 0.U,  // LUI
    (opcode === "b0010111".U) -> imm,  // AUIPC
    (opcode === "b1101111".U) -> 4.U,  // JAL (save PC+4)
    (opcode === "b1100111".U) -> 4.U   // JALR (save PC+4)
  ))

  alu.rs1 := alu_src1
  alu.rs2 := alu_src2
  alu.alu_ctrl := MuxCase(alu_ctrl, Seq(
    (opcode === "b0110111".U) -> ALUOp.ADD,  // LUI: just pass rs2 (imm)
    (opcode === "b0010111".U) -> ALUOp.ADD,  // AUIPC: PC + imm
    (opcode === "b1101111".U) -> ALUOp.ADD,  // JAL: PC + 4
    (opcode === "b1100111".U) -> ALUOp.ADD   // JALR: PC + 4
  ))

  val alu_result = alu.rd

  // Branch condition checking
  val branch_taken = MuxCase(false.B, Seq(
    (funct3 === "b000".U) -> (rs1_data === rs2_data),  // BEQ
    (funct3 === "b001".U) -> (rs1_data =/= rs2_data),  // BNE
    (funct3 === "b100".U) -> (rs1_data.asSInt < rs2_data.asSInt),  // BLT
    (funct3 === "b101".U) -> (rs1_data.asSInt >= rs2_data.asSInt), // BGE
    (funct3 === "b110".U) -> (rs1_data < rs2_data),  // BLTU
    (funct3 === "b111".U) -> (rs1_data >= rs2_data)  // BGEU
  )) && (opcode === "b1100011".U)

  // MEM - Memory Access
  data_mem.mem_ctrl := mem_ctrl
  data_mem.write_addr := alu_result
  data_mem.write_data := rs2_data
  data_mem.read_addr := alu_result
  val mem_data = data_mem.read_data

  // WB - Write Back
  
  // Data to write back to register
  val wb_data = MuxCase(alu_result, Seq(
    (opcode === "b0000011".U) -> mem_data,  // Load
    (opcode === "b0110111".U) -> imm,  // LUI
    (opcode === "b1101111".U) -> (pc + 4.U),  // JAL
    (opcode === "b1100111".U) -> (pc + 4.U)   // JALR
  ))

  // Register write enable
  val reg_write = MuxCase(false.B, Seq(
    (opcode === "b0110011".U) -> true.B,  // R-type
    (opcode === "b0010011".U) -> true.B,  // I-type ALU
    (opcode === "b0000011".U) -> true.B,  // Load
    (opcode === "b0110111".U) -> true.B,  // LUI
    (opcode === "b0010111".U) -> true.B,  // AUIPC
    (opcode === "b1101111".U) -> true.B,  // JAL
    (opcode === "b1100111".U) -> true.B   // JALR
  ))

  regfile.rd_addr := rd
  regfile.write_data := wb_data
  regfile.rd_we := reg_write && (rd =/= 0.U)

  // PC Update
  next_pc := MuxCase(pc + 4.U, Seq(
    branch_taken -> (pc + imm),  // Branch
    (opcode === "b1101111".U) -> (pc + imm),  // JAL
    (opcode === "b1100111".U) -> ((rs1_data + imm) & "hfffffffe".U)  // JALR
  ))

  pc := next_pc

  // Debug Outputs
  debug_pc := pc
  debug_inst := inst
  debug_reg_write := reg_write && (rd =/= 0.U)
  debug_reg_addr := rd
  debug_reg_data := wb_data
}

object RV32CPU extends App {
  VerilogEmitter.parse(new RV32CPU, "rv32_cpu.sv", info = true, lowering=true)
}
