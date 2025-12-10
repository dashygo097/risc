package core

import core.ex._
import chisel3._
import chisel3.util._
import utils._

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
  val alu       = Module(new RV32ALU)
  val ctrl_unit = Module(new RV32GloblCtrlUnit)
  val regfile   = Module(new RV32RegFile)

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
  val id_opcode = if_id.ID_INST(6, 0)
  val id_rd     = if_id.ID_INST(11, 7)
  val id_funct3 = if_id.ID_INST(14, 12)
  val id_rs1    = if_id.ID_INST(19, 15)
  val id_rs2    = if_id.ID_INST(24, 20)
  val id_funct7 = if_id.ID_INST(31, 25)

  // Control unit
  ctrl_unit.inst := if_id.ID_INST
  val id_alu_ctrl = ctrl_unit.alu_ctrl
  val id_mem_ctrl = ctrl_unit.mem_ctrl

  // Immediate generation
  val id_imm_i = Cat(Fill(21, if_id.ID_INST(31)), if_id.ID_INST(30, 20))
  val id_imm_s = Cat(Fill(21, if_id.ID_INST(31)), if_id.ID_INST(30, 25), if_id.ID_INST(11, 7))
  val id_imm_b =
    Cat(
      Fill(20, if_id.ID_INST(31)),
      if_id.ID_INST(7),
      if_id.ID_INST(30, 25),
      if_id.ID_INST(11, 8),
      0.U(1.W)
    )
  val id_imm_u = Cat(if_id.ID_INST(31, 12), Fill(12, 0.U))
  val id_imm_j =
    Cat(
      Fill(12, if_id.ID_INST(31)),
      if_id.ID_INST(19, 12),
      if_id.ID_INST(20),
      if_id.ID_INST(30, 21),
      0.U(1.W)
    )

  val id_imm = MuxCase(
    0.U,
    Seq(
      (id_opcode === "b0010011".U) -> id_imm_i,
      (id_opcode === "b0000011".U) -> id_imm_i,
      (id_opcode === "b0100011".U) -> id_imm_s,
      (id_opcode === "b1100011".U) -> id_imm_b,
      (id_opcode === "b0110111".U) -> id_imm_u,
      (id_opcode === "b0010111".U) -> id_imm_u,
      (id_opcode === "b1101111".U) -> id_imm_j,
      (id_opcode === "b1100111".U) -> id_imm_i
    )
  )

  // Register file reads
  regfile.rs1_addr := id_rs1
  regfile.rs2_addr := id_rs2

  // Forwarding and hazard detection
  val ex_forward_rs1  = id_ex.EX_REG_WRITE && (id_ex.EX_RD =/= 0.U) && (id_ex.EX_RD === id_rs1)
  val ex_forward_rs2  = id_ex.EX_REG_WRITE && (id_ex.EX_RD =/= 0.U) && (id_ex.EX_RD === id_rs2)
  val mem_forward_rs1 =
    ex_mem.MEM_REG_WRITE && (ex_mem.MEM_RD =/= 0.U) && (ex_mem.MEM_RD === id_rs1)
  val mem_forward_rs2 =
    ex_mem.MEM_REG_WRITE && (ex_mem.MEM_RD =/= 0.U) && (ex_mem.MEM_RD === id_rs2)
  val wb_forward_rs1  = mem_wb.WB_REG_WRITE && (mem_wb.WB_RD =/= 0.U) && (mem_wb.WB_RD === id_rs1)
  val wb_forward_rs2  = mem_wb.WB_REG_WRITE && (mem_wb.WB_RD =/= 0.U) && (mem_wb.WB_RD === id_rs2)

  // Load-use hazard detection
  val load_use_hazard = id_ex.EX_MEM_READ &&
    ((id_ex.EX_RD === id_rs1) || (id_ex.EX_RD === id_rs2)) &&
    (id_ex.EX_RD =/= 0.U)

  stall := load_use_hazard

  // Branch/Jump control
  val id_is_branch = id_opcode === "b1100011".U
  val id_is_jal    = id_opcode === "b1101111".U
  val id_is_jalr   = id_opcode === "b1100111".U

  // Forwarded register values for branch comparison
  val id_rs1_data_raw = regfile.rs1_data
  val id_rs2_data_raw = regfile.rs2_data

  val id_rs1_data = MuxCase(
    id_rs1_data_raw,
    Seq(
      wb_forward_rs1  -> mem_wb.WB_DATA,
      mem_forward_rs1 -> ex_mem.MEM_ALU_RESULT,
      ex_forward_rs1  -> alu.rd
    )
  )

  val id_rs2_data = MuxCase(
    id_rs2_data_raw,
    Seq(
      wb_forward_rs2  -> mem_wb.WB_DATA,
      mem_forward_rs2 -> ex_mem.MEM_ALU_RESULT,
      ex_forward_rs2  -> alu.rd
    )
  )

  val id_branch_taken = MuxCase(
    false.B,
    Seq(
      (id_funct3 === "b000".U) -> (id_rs1_data === id_rs2_data),
      (id_funct3 === "b001".U) -> (id_rs1_data =/= id_rs2_data),
      (id_funct3 === "b100".U) -> (id_rs1_data.asSInt < id_rs2_data.asSInt),
      (id_funct3 === "b101".U) -> (id_rs1_data.asSInt >= id_rs2_data.asSInt),
      (id_funct3 === "b110".U) -> (id_rs1_data < id_rs2_data),
      (id_funct3 === "b111".U) -> (id_rs1_data >= id_rs2_data)
    )
  ) && id_is_branch

  flush := id_branch_taken || id_is_jal || id_is_jalr

  val id_reg_write = MuxCase(
    false.B,
    Seq(
      (id_opcode === "b0110011".U) -> true.B,
      (id_opcode === "b0010011".U) -> true.B,
      (id_opcode === "b0000011".U) -> true.B,
      (id_opcode === "b0110111".U) -> true.B,
      (id_opcode === "b0010111".U) -> true.B,
      (id_opcode === "b1101111".U) -> true.B,
      (id_opcode === "b1100111".U) -> true.B
    )
  )

  val id_mem_read  = id_opcode === "b0000011".U
  val id_mem_write = id_opcode === "b0100011".U

  // ID/EX
  id_ex.STALL        := stall
  id_ex.FLUSH        := flush || stall
  id_ex.ID_ALU_CTRL  := id_alu_ctrl
  id_ex.ID_MEM_CTRL  := id_mem_ctrl
  id_ex.ID_REG_WRITE := id_reg_write
  id_ex.ID_MEM_READ  := id_mem_read
  id_ex.ID_MEM_WRITE := id_mem_write
  id_ex.ID_PC        := if_id.ID_PC
  id_ex.ID_INST      := if_id.ID_INST
  id_ex.ID_RS1_DATA  := id_rs1_data
  id_ex.ID_RS2_DATA  := id_rs2_data
  id_ex.ID_IMM       := id_imm
  id_ex.ID_RD        := id_rd
  id_ex.ID_RS1       := id_rs1
  id_ex.ID_RS2       := id_rs2
  id_ex.ID_FUNCT3    := id_funct3
  id_ex.ID_OPCODE    := id_opcode

  // EX Stage
  val ex_opcode = id_ex.EX_OPCODE
  val ex_pc     = id_ex.EX_PC
  val ex_inst   = id_ex.EX_INST
  val ex_imm    = id_ex.EX_IMM

  // ALU source selection with forwarding
  val ex_rs1_data_forwarded = MuxCase(
    id_ex.EX_RS1_DATA,
    Seq(
      (ex_mem.MEM_REG_WRITE && (ex_mem.MEM_RD === id_ex.EX_RS1) && (ex_mem.MEM_RD =/= 0.U)) -> ex_mem.MEM_ALU_RESULT,
      (mem_wb.WB_REG_WRITE && (mem_wb.WB_RD === id_ex.EX_RS1) && (mem_wb.WB_RD =/= 0.U))    -> mem_wb.WB_DATA
    )
  )

  val ex_rs2_data_forwarded = MuxCase(
    id_ex.EX_RS2_DATA,
    Seq(
      (ex_mem.MEM_REG_WRITE && (ex_mem.MEM_RD === id_ex.EX_RS2) && (ex_mem.MEM_RD =/= 0.U)) -> ex_mem.MEM_ALU_RESULT,
      (mem_wb.WB_REG_WRITE && (mem_wb.WB_RD === id_ex.EX_RS2) && (mem_wb.WB_RD =/= 0.U))    -> mem_wb.WB_DATA
    )
  )

  val ex_alu_src1 = MuxCase(
    ex_rs1_data_forwarded,
    Seq(
      (ex_opcode === "b0010111".U) -> ex_pc,
      (ex_opcode === "b1101111".U) -> ex_pc,
      (ex_opcode === "b1100111".U) -> ex_rs1_data_forwarded
    )
  )

  val ex_alu_src2 = MuxCase(
    ex_rs2_data_forwarded,
    Seq(
      (ex_opcode === "b0010011".U) -> ex_imm,
      (ex_opcode === "b0000011".U) -> ex_imm,
      (ex_opcode === "b0100011".U) -> ex_imm,
      (ex_opcode === "b0110111".U) -> 0.U,
      (ex_opcode === "b0010111".U) -> ex_imm,
      (ex_opcode === "b1101111".U) -> 4.U,
      (ex_opcode === "b1100111".U) -> 4.U
    )
  )

  alu.rs1      := ex_alu_src1
  alu.rs2      := ex_alu_src2
  alu.alu_ctrl := MuxCase(
    id_ex.EX_ALU_CTRL,
    Seq(
      (ex_opcode === "b0110111".U) -> ALUOp.ADD,
      (ex_opcode === "b0010111".U) -> ALUOp.ADD,
      (ex_opcode === "b1101111".U) -> ALUOp.ADD,
      (ex_opcode === "b1100111".U) -> ALUOp.ADD
    )
  )

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
      "b000".U -> (mem_rs2_data << (mem_byte_addr << 3)),
      "b001".U -> (mem_rs2_data << (mem_byte_addr(1) << 4)),
      "b010".U -> mem_rs2_data
    )
  )
  DMEM_WRITE_DATA := mem_aligned_write_data

  DMEM_WRITE_STRB := MuxLookup(mem_funct3, 0.U)(
    Seq(
      "b000".U -> ("b0001".U << mem_byte_addr),
      "b001".U -> ("b0011".U << (mem_byte_addr(1) << 1)),
      "b010".U -> "b1111".U
    )
  )

  val mem_shifted_read_data = DMEM_READ_DATA >> (mem_byte_addr << 3)
  val mem_data              = MuxLookup(mem_funct3, 0.U)(
    Seq(
      "b000".U -> Cat(Fill(24, mem_shifted_read_data(7)), mem_shifted_read_data(7, 0)),
      "b001".U -> Cat(Fill(16, mem_shifted_read_data(15)), mem_shifted_read_data(15, 0)),
      "b010".U -> DMEM_READ_DATA,
      "b100".U -> Cat(Fill(24, 0.U), mem_shifted_read_data(7, 0)),
      "b101".U -> Cat(Fill(16, 0.U), mem_shifted_read_data(15, 0))
    )
  )

  val mem_wb_data = MuxCase(
    mem_alu_result,
    Seq(
      (mem_opcode === "b0000011".U) -> mem_data,
      (mem_opcode === "b0110111".U) -> ex_mem.MEM_IMM,
      (mem_opcode === "b1101111".U) -> (ex_mem.MEM_PC + 4.U),
      (mem_opcode === "b1100111".U) -> (ex_mem.MEM_PC + 4.U)
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
      id_is_jal       -> (if_id.ID_PC + id_imm),
      id_is_jalr      -> ((id_rs1_data + id_imm) & "hfffffffe".U)
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
