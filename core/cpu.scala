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
  val alu                = Module(new RV32ALU)
  val decoder            = Module(new RV32Decoder)
  val imm_gen            = Module(new RV32ImmGen)
  val regfile            = Module(new RV32RegFile)
  val id_forwarding_unit = Module(new RV32IDForwardingUnit)

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
  id_forwarding_unit.id_rs1        := decoder.rs1
  id_forwarding_unit.id_rs2        := decoder.rs2
  id_forwarding_unit.ex_rd         := id_ex.EX_RD
  id_forwarding_unit.ex_reg_write  := id_ex.EX_REG_WRITE
  id_forwarding_unit.mem_rd        := ex_mem.MEM_RD
  id_forwarding_unit.mem_reg_write := ex_mem.MEM_REG_WRITE
  id_forwarding_unit.wb_rd         := mem_wb.WB_RD
  id_forwarding_unit.wb_reg_write  := mem_wb.WB_REG_WRITE

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
  val id_rs1_data = MuxCase(
    regfile.rs1_data,
    Seq(
      (id_forwarding_unit.forward_rs1 === ForwardingStage.MEM) -> ex_mem.MEM_ALU_RESULT,
      (id_forwarding_unit.forward_rs1 === ForwardingStage.WB)  -> mem_wb.WB_DATA
    )
  )
  val id_rs2_data = MuxCase(
    regfile.rs2_data,
    Seq(
      (id_forwarding_unit.forward_rs2 === ForwardingStage.MEM) -> ex_mem.MEM_ALU_RESULT,
      (id_forwarding_unit.forward_rs2 === ForwardingStage.WB)  -> mem_wb.WB_DATA
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
  id_ex.ID_ALU_OP     := decoder.alu_op
  id_ex.ID_ALU_IS_SUB := decoder.alu_is_sub
  id_ex.ID_ALU_IS_SRA := decoder.alu_is_sra
  id_ex.ID_MEM_OP     := decoder.mem_op
  id_ex.ID_REG_WRITE  := decoder.reg_write
  id_ex.ID_MEM_READ   := decoder.mem_read
  id_ex.ID_MEM_WRITE  := decoder.mem_write

  id_ex.ID_IS_ALU     := decoder.is_alu
  id_ex.ID_IS_ALU_IMM := decoder.is_alu_imm
  id_ex.ID_IS_LOAD    := decoder.is_load
  id_ex.ID_IS_STORE   := decoder.is_store
  id_ex.ID_IS_BRANCH  := decoder.is_branch
  id_ex.ID_IS_JAL     := decoder.is_jal
  id_ex.ID_IS_JALR    := decoder.is_jalr
  id_ex.ID_IS_LUI     := decoder.is_lui
  id_ex.ID_IS_AUIPC   := decoder.is_auipc
  id_ex.ID_IS_SYSTEM  := decoder.is_system
  id_ex.ID_IS_FENCE   := decoder.is_fence

  id_ex.ID_PC       := if_id.ID_PC
  id_ex.ID_INST     := if_id.ID_INST
  id_ex.ID_RS1_DATA := id_rs1_data
  id_ex.ID_RS2_DATA := id_rs2_data
  id_ex.ID_IMM      := id_imm
  id_ex.ID_RD       := decoder.rd
  id_ex.ID_RS1      := decoder.rs1
  id_ex.ID_RS2      := decoder.rs2
  id_ex.ID_FUNCT3   := decoder.funct3
  id_ex.ID_OPCODE   := decoder.opcode

  // EX Stage

  // ALU source selection with forwarding
  val ex_rs1_data_forwarded = Mux(
    ex_mem.MEM_REG_WRITE && (ex_mem.MEM_RD === id_ex.EX_RS1) && (ex_mem.MEM_RD =/= 0.U),
    ex_mem.MEM_ALU_RESULT,
    Mux(
      mem_wb.WB_REG_WRITE && (mem_wb.WB_RD === id_ex.EX_RS1) && (mem_wb.WB_RD =/= 0.U),
      mem_wb.WB_DATA,
      id_ex.EX_RS1_DATA
    )
  )

  val ex_rs2_data_forwarded = Mux(
    ex_mem.MEM_REG_WRITE && (ex_mem.MEM_RD === id_ex.EX_RS2) && (ex_mem.MEM_RD =/= 0.U),
    ex_mem.MEM_ALU_RESULT,
    Mux(
      mem_wb.WB_REG_WRITE && (mem_wb.WB_RD === id_ex.EX_RS2) && (mem_wb.WB_RD =/= 0.U),
      mem_wb.WB_DATA,
      id_ex.EX_RS2_DATA
    )
  )

  val ex_alu_src1 = MuxCase(
    ex_rs1_data_forwarded,
    Seq(
      id_ex.EX_IS_AUIPC -> id_ex.EX_PC,
      id_ex.EX_IS_JAL   -> id_ex.EX_PC,
      id_ex.EX_IS_JALR  -> ex_rs1_data_forwarded
    )
  )

  val ex_alu_src2 = MuxCase(
    ex_rs2_data_forwarded,
    Seq(
      id_ex.EX_IS_ALU_IMM -> id_ex.EX_IMM,
      id_ex.EX_IS_LOAD    -> id_ex.EX_IMM,
      id_ex.EX_IS_STORE   -> id_ex.EX_IMM,
      id_ex.EX_IS_LUI     -> 0.U,
      id_ex.EX_IS_AUIPC   -> id_ex.EX_IMM,
      id_ex.EX_IS_JAL     -> 4.U,
      id_ex.EX_IS_JALR    -> 4.U
    )
  )

  alu.rs1        := ex_alu_src1
  alu.rs2        := ex_alu_src2
  alu.alu_op     := id_ex.EX_ALU_OP
  alu.is_alu_sub := id_ex.EX_ALU_IS_SUB
  alu.is_alu_sra := id_ex.EX_ALU_IS_SRA

  val ex_alu_result = alu.rd

  // EX/MEM
  ex_mem.STALL         := false.B
  ex_mem.FLUSH         := false.B
  ex_mem.EX_MEM_CTRL   := id_ex.EX_MEM_OP
  ex_mem.EX_REG_WRITE  := id_ex.EX_REG_WRITE
  ex_mem.EX_MEM_READ   := id_ex.EX_MEM_READ
  ex_mem.EX_MEM_WRITE  := id_ex.EX_MEM_WRITE
  ex_mem.EX_ALU_RESULT := ex_alu_result
  ex_mem.EX_RS2_DATA   := ex_rs2_data_forwarded
  ex_mem.EX_RD         := id_ex.EX_RD
  ex_mem.EX_FUNCT3     := id_ex.EX_FUNCT3
  ex_mem.EX_PC         := id_ex.EX_PC
  ex_mem.EX_OPCODE     := id_ex.EX_OPCODE
  ex_mem.EX_INST       := id_ex.EX_INST
  ex_mem.EX_IMM        := id_ex.EX_IMM

  // MEM Stage
  DMEM_READ_EN  := ex_mem.MEM_MEM_READ
  DMEM_WRITE_EN := ex_mem.MEM_MEM_WRITE
  DMEM_ADDR     := ex_mem.MEM_ALU_RESULT

  val mem_byte_addr          = ex_mem.MEM_ALU_RESULT(1, 0)
  val mem_aligned_write_data = MuxLookup(ex_mem.MEM_FUNCT3, ex_mem.MEM_RS2_DATA)(
    Seq(
      StoreOp.SB -> (ex_mem.MEM_RS2_DATA << (mem_byte_addr << 3)),
      StoreOp.SH -> (ex_mem.MEM_RS2_DATA << (mem_byte_addr(1) << 4)),
      StoreOp.SW -> ex_mem.MEM_RS2_DATA
    )
  )
  DMEM_WRITE_DATA := mem_aligned_write_data

  DMEM_WRITE_STRB := MuxLookup(ex_mem.MEM_FUNCT3, 0.U)(
    Seq(
      StoreOp.SB -> ("b0001".U << mem_byte_addr),
      StoreOp.SH -> ("b0011".U << (mem_byte_addr(1) << 1)),
      StoreOp.SW -> "b1111".U
    )
  )

  val mem_shifted_read_data = DMEM_READ_DATA >> (mem_byte_addr << 3)
  val mem_data              = MuxLookup(ex_mem.MEM_FUNCT3, 0.U)(
    Seq(
      LoadOp.LB  -> Cat(Fill(24, mem_shifted_read_data(7)), mem_shifted_read_data(7, 0)),
      LoadOp.LH  -> Cat(Fill(16, mem_shifted_read_data(15)), mem_shifted_read_data(15, 0)),
      LoadOp.LW  -> DMEM_READ_DATA,
      LoadOp.LBU -> Cat(Fill(24, 0.U), mem_shifted_read_data(7, 0)),
      LoadOp.LHU -> Cat(Fill(16, 0.U), mem_shifted_read_data(15, 0))
    )
  )

  val mem_wb_data = MuxCase(
    ex_mem.MEM_ALU_RESULT,
    Seq(
      (ex_mem.MEM_OPCODE === "b0000011".U) -> mem_data,              // Load
      (ex_mem.MEM_OPCODE === "b0110111".U) -> ex_mem.MEM_IMM,        // LUI
      (ex_mem.MEM_OPCODE === "b1101111".U) -> (ex_mem.MEM_PC + 4.U), // JAL
      (ex_mem.MEM_OPCODE === "b1100111".U) -> (ex_mem.MEM_PC + 4.U)  // JALR
    )
  )

  // MEM/WB
  mem_wb.STALL         := false.B
  mem_wb.FLUSH         := false.B
  mem_wb.MEM_REG_WRITE := ex_mem.MEM_REG_WRITE
  mem_wb.MEM_WB_DATA   := mem_wb_data
  mem_wb.MEM_RD        := ex_mem.MEM_RD
  mem_wb.MEM_PC        := ex_mem.MEM_PC
  mem_wb.MEM_OPCODE    := ex_mem.MEM_OPCODE
  mem_wb.MEM_INST      := ex_mem.MEM_INST

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
