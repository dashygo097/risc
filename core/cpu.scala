package core

import common._
import id._
import ex._
import utils._
import chisel3._
import chisel3.util._

class RV32CPU extends Module {
  override def desiredName: String = s"rv32_cpu"

  // Memory Interface
  val IMEM_ADDR = IO(Output(UInt(32.W)))
  val IMEM_INST = IO(Input(UInt(32.W)))

  val DMEM_READ_EN    = IO(Output(Bool()))
  val DMEM_WRITE_EN   = IO(Output(Bool()))
  val DMEM_ADDR       = IO(Output(UInt(32.W)))
  val DMEM_WRITE_DATA = IO(Output(UInt(32.W)))
  val DMEM_WRITE_STRB = IO(Output(UInt(4.W)))
  val DMEM_READ_DATA  = IO(Input(UInt(32.W)))

  // Debug
  val DEBUG_PC       = IO(Output(UInt(32.W)))
  val DEBUG_INST     = IO(Output(UInt(32.W)))
  val DEBUG_REG_WE   = IO(Output(Bool()))
  val DEBUG_REG_ADDR = IO(Output(UInt(5.W)))
  val DEBUG_REG_DATA = IO(Output(UInt(32.W)))

  // Modules
  val alu     = Module(new RV32ALU)
  val decoder = Module(new RV32Decoder)
  val imm_gen = Module(new RV32ImmGen)
  val regfile = Module(new RV32RegFile)

  // Pipeline
  val if_id  = Module(new IF_ID)
  val id_ex  = Module(new ID_EX)
  val ex_mem = Module(new EX_MEM)
  val mem_wb = Module(new MEM_WB)

  // Control signals
  val stall = Wire(Bool())
  val flush = Wire(Bool())

  // IF
  val pc      = RegInit(0.U(32.W))
  val next_pc = Wire(UInt(32.W))

  IMEM_ADDR := pc

  // IF/ID
  if_id.STALL   := stall
  if_id.FLUSH   := flush
  if_id.IF_PC   := pc
  if_id.IF_INST := IMEM_INST

  // ID Stage
  decoder.inst := if_id.ID_INST

  // Immediate generation
  imm_gen.inst := if_id.ID_INST
  val id_imm = imm_gen.imm

  // Register file reads
  regfile.rs1_addr := decoder.rs1
  regfile.rs2_addr := decoder.rs2

  // Forwarding and hazard detection
  val ex_forward_rs1  = id_ex.EX_REG_WRITE && (id_ex.EX_RD =/= 0.U) && (id_ex.EX_RD === decoder.rs1)
  val ex_forward_rs2  = id_ex.EX_REG_WRITE && (id_ex.EX_RD =/= 0.U) && (id_ex.EX_RD === decoder.rs2)
  val mem_forward_rs1 =
    ex_mem.MEM_REG_WRITE && (ex_mem.MEM_RD =/= 0.U) && (ex_mem.MEM_RD === decoder.rs1)
  val mem_forward_rs2 =
    ex_mem.MEM_REG_WRITE && (ex_mem.MEM_RD =/= 0.U) && (ex_mem.MEM_RD === decoder.rs2)
  val wb_forward_rs1  =
    mem_wb.WB_REG_WRITE && (mem_wb.WB_RD =/= 0.U) && (mem_wb.WB_RD === decoder.rs1)
  val wb_forward_rs2  =
    mem_wb.WB_REG_WRITE && (mem_wb.WB_RD =/= 0.U) && (mem_wb.WB_RD === decoder.rs2)

  // Load-use hazard detection
  val load_use_hazard    = id_ex.EX_MEM_READ &&
    ((id_ex.EX_RD === decoder.rs1) || (id_ex.EX_RD === decoder.rs2)) &&
    (id_ex.EX_RD =/= 0.U)
  val branch_load_hazard = (decoder.is_branch || decoder.is_jalr) &&
    id_ex.EX_MEM_READ &&
    ((id_ex.EX_RD === decoder.rs1) || (id_ex.EX_RD === decoder.rs2)) &&
    (id_ex.EX_RD =/= 0.U)
  val branch_ex_hazard   = (decoder.is_branch || decoder.is_jalr) &&
    id_ex.EX_REG_WRITE &&
    !id_ex.EX_MEM_READ &&
    ((id_ex.EX_RD === decoder.rs1) || (decoder.is_branch && id_ex.EX_RD === decoder.rs2)) &&
    (id_ex.EX_RD =/= 0.U)

  stall := load_use_hazard || branch_load_hazard || branch_ex_hazard

  // Forwarded register values for branch comparison
  val id_rs1_data_raw = regfile.rs1_data
  val id_rs2_data_raw = regfile.rs2_data

  val id_rs1_data = Mux(
    mem_forward_rs1,
    ex_mem.MEM_ALU_RESULT,
    Mux(
      wb_forward_rs1,
      mem_wb.WB_DATA,
      id_rs1_data_raw
    )
  )

  val id_rs2_data = Mux(
    mem_forward_rs2,
    ex_mem.MEM_ALU_RESULT,
    Mux(
      wb_forward_rs2,
      mem_wb.WB_DATA,
      id_rs2_data_raw
    )
  )

  val id_branch_taken = MuxCase(
    false.B,
    Seq(
      (decoder.funct3 === BranchOp.BEQ)  -> (id_rs1_data === id_rs2_data),
      (decoder.funct3 === BranchOp.BNE)  -> (id_rs1_data =/= id_rs2_data),
      (decoder.funct3 === BranchOp.BLT)  -> (id_rs1_data.asSInt < id_rs2_data.asSInt),
      (decoder.funct3 === BranchOp.BGE)  -> (id_rs1_data.asSInt >= id_rs2_data.asSInt),
      (decoder.funct3 === BranchOp.BLTU) -> (id_rs1_data < id_rs2_data),
      (decoder.funct3 === BranchOp.BGEU) -> (id_rs1_data >= id_rs2_data)
    )
  ) && decoder.is_branch

  flush := id_branch_taken || decoder.is_jal || decoder.is_jalr

  // ID/EX
  id_ex.STALL         := stall
  id_ex.FLUSH         := flush || stall
  id_ex.ID_ALU_OP_R   := decoder.alu_op_r
  id_ex.ID_ALU_IS_SUB := decoder.alu_is_sub
  id_ex.ID_ALU_IS_SRA := decoder.alu_is_sra
  id_ex.ID_MEM_CTRL   := decoder.mem_ctrl
  id_ex.ID_REG_WRITE  := decoder.reg_write
  id_ex.ID_MEM_READ   := decoder.mem_read
  id_ex.ID_MEM_WRITE  := decoder.mem_write
  id_ex.ID_PC         := if_id.ID_PC
  id_ex.ID_INST       := if_id.ID_INST
  id_ex.ID_RS1_DATA   := id_rs1_data
  id_ex.ID_RS2_DATA   := id_rs2_data
  id_ex.ID_IMM        := id_imm
  id_ex.ID_RD         := decoder.rd
  id_ex.ID_RS1        := decoder.rs1
  id_ex.ID_RS2        := decoder.rs2
  id_ex.ID_FUNCT3     := decoder.funct3
  id_ex.ID_OPCODE     := decoder.opcode

  // EX Stage
  val ex_opcode = id_ex.EX_OPCODE
  val ex_pc     = id_ex.EX_PC
  val ex_inst   = id_ex.EX_INST
  val ex_imm    = id_ex.EX_IMM

  // ALU source selection with forwarding
  val ex_rs1_data_forwarded = MuxCase(
    id_ex.EX_RS1_DATA,
    Seq(
      (mem_wb.WB_REG_WRITE && (mem_wb.WB_RD === id_ex.EX_RS1) && (mem_wb.WB_RD =/= 0.U))    -> mem_wb.WB_DATA,
      (ex_mem.MEM_REG_WRITE && (ex_mem.MEM_RD === id_ex.EX_RS1) && (ex_mem.MEM_RD =/= 0.U)) -> ex_mem.MEM_ALU_RESULT,
    )
  )

  val ex_rs2_data_forwarded = MuxCase(
    id_ex.EX_RS2_DATA,
    Seq(
      (mem_wb.WB_REG_WRITE && (mem_wb.WB_RD === id_ex.EX_RS2) && (mem_wb.WB_RD =/= 0.U))    -> mem_wb.WB_DATA,
      (ex_mem.MEM_REG_WRITE && (ex_mem.MEM_RD === id_ex.EX_RS2) && (ex_mem.MEM_RD =/= 0.U)) -> ex_mem.MEM_ALU_RESULT,
    )
  )

  val ex_alu_src1 = MuxCase(
    ex_rs1_data_forwarded,
    Seq(
      (ex_opcode === "b0010111".U) -> ex_pc,                // AUIPC
      (ex_opcode === "b1101111".U) -> ex_pc,                // JAL
      (ex_opcode === "b1100111".U) -> ex_rs1_data_forwarded // JALR
    )
  )

  val ex_alu_src2 = MuxCase(
    ex_rs2_data_forwarded,
    Seq(
      (ex_opcode === "b0010011".U) -> ex_imm, // I-type ALU
      (ex_opcode === "b0000011".U) -> ex_imm, // Load
      (ex_opcode === "b0100011".U) -> ex_imm, // Store
      (ex_opcode === "b0110111".U) -> 0.U,    // LUI
      (ex_opcode === "b0010111".U) -> ex_imm, // AUIPC
      (ex_opcode === "b1101111".U) -> 4.U,    // JAL
      (ex_opcode === "b1100111".U) -> 4.U     // JALR
    )
  )

  alu.rs1        := ex_alu_src1
  alu.rs2        := ex_alu_src2
  alu.alu_op_r   := MuxCase(
    id_ex.EX_ALU_OP_R,
    Seq(
      (ex_opcode === "b0110111".U) -> ALUOp.ADD, // LUI
      (ex_opcode === "b0010111".U) -> ALUOp.ADD, // AUIPC
      (ex_opcode === "b1101111".U) -> ALUOp.ADD, // JAL
      (ex_opcode === "b1100111".U) -> ALUOp.ADD  // JALR
    )
  )
  alu.is_alu_sub := id_ex.EX_ALU_IS_SUB
  alu.is_alu_sra := id_ex.EX_ALU_IS_SRA

  val ex_alu_result = alu.rd

  // EX/MEM
  ex_mem.STALL         := false.B
  ex_mem.FLUSH         := false.B
  ex_mem.EX_MEM_CTRL   := id_ex.EX_MEM_CTRL
  ex_mem.EX_REG_WRITE  := id_ex.EX_REG_WRITE
  ex_mem.EX_MEM_READ   := id_ex.EX_MEM_READ
  ex_mem.EX_MEM_WRITE  := id_ex.EX_MEM_WRITE
  ex_mem.EX_ALU_RESULT := ex_alu_result
  ex_mem.EX_RS2_DATA   := ex_rs2_data_forwarded
  ex_mem.EX_RD         := id_ex.EX_RD
  ex_mem.EX_FUNCT3     := id_ex.EX_FUNCT3
  ex_mem.EX_PC         := ex_pc
  ex_mem.EX_OPCODE     := ex_opcode
  ex_mem.EX_INST       := ex_inst
  ex_mem.EX_IMM        := ex_imm

  // MEM Stage
  val mem_opcode     = ex_mem.MEM_OPCODE
  val mem_funct3     = ex_mem.MEM_FUNCT3
  val mem_alu_result = ex_mem.MEM_ALU_RESULT
  val mem_rs2_data   = ex_mem.MEM_RS2_DATA
  val mem_inst       = ex_mem.MEM_INST

  DMEM_READ_EN  := ex_mem.MEM_MEM_READ
  DMEM_WRITE_EN := ex_mem.MEM_MEM_WRITE
  DMEM_ADDR     := mem_alu_result

  val mem_byte_addr          = mem_alu_result(1, 0)
  val mem_aligned_write_data = MuxLookup(mem_funct3, mem_rs2_data)(
    Seq(
      "b000".U -> (mem_rs2_data << (mem_byte_addr << 3)),    // SB
      "b001".U -> (mem_rs2_data << (mem_byte_addr(1) << 4)), // SH
      "b010".U -> mem_rs2_data                               // SW
    )
  )
  DMEM_WRITE_DATA := mem_aligned_write_data

  DMEM_WRITE_STRB := MuxLookup(mem_funct3, 0.U)(
    Seq(
      "b000".U -> ("b0001".U << mem_byte_addr),           // SB
      "b001".U -> ("b0011".U << (mem_byte_addr(1) << 1)), // SH
      "b010".U -> "b1111".U                               // SW
    )
  )

  val mem_shifted_read_data = DMEM_READ_DATA >> (mem_byte_addr << 3)
  val mem_data              = MuxLookup(mem_funct3, 0.U)(
    Seq(
      "b000".U -> Cat(Fill(24, mem_shifted_read_data(7)), mem_shifted_read_data(7, 0)),   // LB
      "b001".U -> Cat(Fill(16, mem_shifted_read_data(15)), mem_shifted_read_data(15, 0)), // LH
      "b010".U -> DMEM_READ_DATA,                                                         // LW
      "b100".U -> Cat(Fill(24, 0.U), mem_shifted_read_data(7, 0)),                        // LBU
      "b101".U -> Cat(Fill(16, 0.U), mem_shifted_read_data(15, 0))                        // LHU
    )
  )

  val mem_wb_data = MuxCase(
    mem_alu_result,
    Seq(
      (mem_opcode === "b0000011".U) -> mem_data,              // Load
      (mem_opcode === "b0110111".U) -> ex_mem.MEM_IMM,        // LUI
      (mem_opcode === "b1101111".U) -> (ex_mem.MEM_PC + 4.U), // JAL
      (mem_opcode === "b1100111".U) -> (ex_mem.MEM_PC + 4.U)  // JALR
    )
  )

  // MEM/WB
  mem_wb.STALL         := false.B
  mem_wb.FLUSH         := false.B
  mem_wb.MEM_REG_WRITE := ex_mem.MEM_REG_WRITE
  mem_wb.MEM_WB_DATA   := mem_wb_data
  mem_wb.MEM_RD        := ex_mem.MEM_RD
  mem_wb.MEM_PC        := ex_mem.MEM_PC
  mem_wb.MEM_OPCODE    := mem_opcode
  mem_wb.MEM_INST      := mem_inst

  // WB Stage
  regfile.write_addr := mem_wb.WB_RD
  regfile.write_data := mem_wb.WB_DATA
  regfile.write_en   := mem_wb.WB_REG_WRITE && (mem_wb.WB_RD =/= 0.U)

  // PC Update
  next_pc := MuxCase(
    pc + 4.U,
    Seq(
      id_branch_taken -> (if_id.ID_PC + id_imm),
      decoder.is_jal  -> (if_id.ID_PC + id_imm),
      decoder.is_jalr -> ((id_rs1_data + id_imm) & "hfffffffe".U)
    )
  )

  when(!stall) {
    pc := next_pc
  }

  // Debug
  DEBUG_PC       := mem_wb.WB_PC
  DEBUG_INST     := mem_wb.WB_INST
  DEBUG_REG_WE   := mem_wb.WB_REG_WRITE && (mem_wb.WB_RD =/= 0.U)
  DEBUG_REG_ADDR := mem_wb.WB_RD
  DEBUG_REG_DATA := mem_wb.WB_DATA
}

object RV32CPU extends App {
  VerilogEmitter.parse(new RV32CPU, "rv32_cpu.sv", info = true, lowering = true)
}
