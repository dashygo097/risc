package core

import core.ex._
import chisel3._
import chisel3.util._
import utils._

class RV32CPU extends Module {
  override def desiredName: String = s"rv32_cpu"

  // Memory Interface
  val imem_addr = IO(Output(UInt(32.W)))
  val imem_inst = IO(Input(UInt(32.W)))

  val dmem_read_en    = IO(Output(Bool()))
  val dmem_write_en   = IO(Output(Bool()))
  val dmem_addr       = IO(Output(UInt(32.W)))
  val dmem_write_data = IO(Output(UInt(32.W)))
  val dmem_write_strb = IO(Output(UInt(4.W)))
  val dmem_read_data  = IO(Input(UInt(32.W)))

  // Debug
  val debug_pc        = IO(Output(UInt(32.W)))
  val debug_inst      = IO(Output(UInt(32.W)))
  val debug_reg_write = IO(Output(Bool()))
  val debug_reg_addr  = IO(Output(UInt(5.W)))
  val debug_reg_data  = IO(Output(UInt(32.W)))

  // Modules
  val alu       = Module(new RV32ALU)
  val ctrl_unit = Module(new RV32GloblCtrlUnit)
  val regfile   = Module(new RV32RegFile)

  // Pipeline registers
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

  imem_addr := pc
  val if_inst = imem_inst

  // IF/ID
  if_id.stall   := stall
  if_id.flush   := flush
  if_id.if_pc   := pc
  if_id.if_inst := if_inst

  // ID Stage
  val id_pc   = if_id.id_pc
  val id_inst = if_id.id_inst

  val id_opcode = id_inst(6, 0)
  val id_rd     = id_inst(11, 7)
  val id_funct3 = id_inst(14, 12)
  val id_rs1    = id_inst(19, 15)
  val id_rs2    = id_inst(24, 20)
  val id_funct7 = id_inst(31, 25)

  // Control unit
  ctrl_unit.inst := id_inst
  val id_alu_ctrl = ctrl_unit.alu_ctrl
  val id_mem_ctrl = ctrl_unit.mem_ctrl

  // Immediate generation
  val id_imm_i = Cat(Fill(21, id_inst(31)), id_inst(30, 20))
  val id_imm_s = Cat(Fill(21, id_inst(31)), id_inst(30, 25), id_inst(11, 7))
  val id_imm_b = Cat(Fill(20, id_inst(31)), id_inst(7), id_inst(30, 25), id_inst(11, 8), 0.U(1.W))
  val id_imm_u = Cat(id_inst(31, 12), Fill(12, 0.U))
  val id_imm_j = Cat(Fill(12, id_inst(31)), id_inst(19, 12), id_inst(20), id_inst(30, 21), 0.U(1.W))

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

  // Forwarding and hazard detection (kept as in previous version)
  val ex_forward_rs1  = id_ex.ex_reg_write && (id_ex.ex_rd =/= 0.U) && (id_ex.ex_rd === id_rs1)
  val ex_forward_rs2  = id_ex.ex_reg_write && (id_ex.ex_rd =/= 0.U) && (id_ex.ex_rd === id_rs2)
  val mem_forward_rs1 =
    ex_mem.mem_reg_write && (ex_mem.mem_rd =/= 0.U) && (ex_mem.mem_rd === id_rs1)
  val mem_forward_rs2 =
    ex_mem.mem_reg_write && (ex_mem.mem_rd =/= 0.U) && (ex_mem.mem_rd === id_rs2)
  val wb_forward_rs1  = mem_wb.wb_reg_write && (mem_wb.wb_rd =/= 0.U) && (mem_wb.wb_rd === id_rs1)
  val wb_forward_rs2  = mem_wb.wb_reg_write && (mem_wb.wb_rd =/= 0.U) && (mem_wb.wb_rd === id_rs2)

  // Load-use hazard detection
  val load_use_hazard = id_ex.ex_mem_read &&
    ((id_ex.ex_rd === id_rs1) || (id_ex.ex_rd === id_rs2)) &&
    (id_ex.ex_rd =/= 0.U)

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
      wb_forward_rs1  -> mem_wb.wb_wb_data,
      mem_forward_rs1 -> ex_mem.mem_alu_result,
      ex_forward_rs1  -> alu.rd
    )
  )

  val id_rs2_data = MuxCase(
    id_rs2_data_raw,
    Seq(
      wb_forward_rs2  -> mem_wb.wb_wb_data,
      mem_forward_rs2 -> ex_mem.mem_alu_result,
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
  id_ex.stall        := stall
  id_ex.flush        := flush || stall
  id_ex.id_alu_ctrl  := id_alu_ctrl
  id_ex.id_mem_ctrl  := id_mem_ctrl
  id_ex.id_reg_write := id_reg_write
  id_ex.id_mem_read  := id_mem_read
  id_ex.id_mem_write := id_mem_write
  id_ex.id_pc        := id_pc
  id_ex.id_inst      := id_inst
  id_ex.id_rs1_data  := id_rs1_data
  id_ex.id_rs2_data  := id_rs2_data
  id_ex.id_imm       := id_imm
  id_ex.id_rd        := id_rd
  id_ex.id_rs1       := id_rs1
  id_ex.id_rs2       := id_rs2
  id_ex.id_funct3    := id_funct3
  id_ex.id_opcode    := id_opcode

  // EX Stage
  val ex_opcode = id_ex.ex_opcode
  val ex_pc     = id_ex.ex_pc
  val ex_inst   = id_ex.ex_inst
  val ex_imm    = id_ex.ex_imm

  // ALU source selection with forwarding
  val ex_rs1_data_forwarded = MuxCase(
    id_ex.ex_rs1_data,
    Seq(
      (ex_mem.mem_reg_write && (ex_mem.mem_rd === id_ex.ex_rs1) && (ex_mem.mem_rd =/= 0.U)) -> ex_mem.mem_alu_result,
      (mem_wb.wb_reg_write && (mem_wb.wb_rd === id_ex.ex_rs1) && (mem_wb.wb_rd =/= 0.U))    -> mem_wb.wb_wb_data
    )
  )

  val ex_rs2_data_forwarded = MuxCase(
    id_ex.ex_rs2_data,
    Seq(
      (ex_mem.mem_reg_write && (ex_mem.mem_rd === id_ex.ex_rs2) && (ex_mem.mem_rd =/= 0.U)) -> ex_mem.mem_alu_result,
      (mem_wb.wb_reg_write && (mem_wb.wb_rd === id_ex.ex_rs2) && (mem_wb.wb_rd =/= 0.U))    -> mem_wb.wb_wb_data
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
    id_ex.ex_alu_ctrl,
    Seq(
      (ex_opcode === "b0110111".U) -> ALUOp.ADD,
      (ex_opcode === "b0010111".U) -> ALUOp.ADD,
      (ex_opcode === "b1101111".U) -> ALUOp.ADD,
      (ex_opcode === "b1100111".U) -> ALUOp.ADD
    )
  )

  val ex_alu_result = alu.rd

  // EX/MEM
  ex_mem.stall         := false.B
  ex_mem.flush         := false.B
  ex_mem.ex_mem_ctrl   := id_ex.ex_mem_ctrl
  ex_mem.ex_reg_write  := id_ex.ex_reg_write
  ex_mem.ex_mem_read   := id_ex.ex_mem_read
  ex_mem.ex_mem_write  := id_ex.ex_mem_write
  ex_mem.ex_alu_result := ex_alu_result
  ex_mem.ex_rs2_data   := ex_rs2_data_forwarded
  ex_mem.ex_rd         := id_ex.ex_rd
  ex_mem.ex_funct3     := id_ex.ex_funct3
  ex_mem.ex_pc         := ex_pc
  ex_mem.ex_opcode     := ex_opcode
  ex_mem.ex_inst       := ex_inst

  // MEM Stage
  val mem_opcode     = ex_mem.mem_opcode
  val mem_funct3     = ex_mem.mem_funct3
  val mem_alu_result = ex_mem.mem_alu_result
  val mem_rs2_data   = ex_mem.mem_rs2_data
  val mem_inst       = ex_mem.mem_inst

  dmem_read_en  := ex_mem.mem_mem_read
  dmem_write_en := ex_mem.mem_mem_write
  dmem_addr     := mem_alu_result

  val mem_byte_addr          = mem_alu_result(1, 0)
  val mem_aligned_write_data = MuxLookup(mem_funct3, mem_rs2_data)(
    Seq(
      "b000".U -> (mem_rs2_data << (mem_byte_addr << 3)),
      "b001".U -> (mem_rs2_data << (mem_byte_addr(1) << 4)),
      "b010".U -> mem_rs2_data
    )
  )
  dmem_write_data := mem_aligned_write_data

  dmem_write_strb := MuxLookup(mem_funct3, 0.U)(
    Seq(
      "b000".U -> ("b0001".U << mem_byte_addr),
      "b001".U -> ("b0011".U << (mem_byte_addr(1) << 1)),
      "b010".U -> "b1111".U
    )
  )

  val mem_shifted_read_data = dmem_read_data >> (mem_byte_addr << 3)
  val mem_data              = MuxLookup(mem_funct3, 0.U)(
    Seq(
      "b000".U -> Cat(Fill(24, mem_shifted_read_data(7)), mem_shifted_read_data(7, 0)),
      "b001".U -> Cat(Fill(16, mem_shifted_read_data(15)), mem_shifted_read_data(15, 0)),
      "b010".U -> dmem_read_data,
      "b100".U -> Cat(Fill(24, 0.U), mem_shifted_read_data(7, 0)),
      "b101".U -> Cat(Fill(16, 0.U), mem_shifted_read_data(15, 0))
    )
  )

  val mem_wb_data = MuxCase(
    mem_alu_result,
    Seq(
      (mem_opcode === "b0000011".U) -> mem_data,
      (mem_opcode === "b0110111".U) -> mem_alu_result,
      (mem_opcode === "b1101111".U) -> (ex_mem.mem_pc + 4.U),
      (mem_opcode === "b1100111".U) -> (ex_mem.mem_pc + 4.U)
    )
  )

  // MEM/WB
  mem_wb.stall         := false.B
  mem_wb.flush         := false.B
  mem_wb.mem_reg_write := ex_mem.mem_reg_write
  mem_wb.mem_wb_data   := mem_wb_data
  mem_wb.mem_rd        := ex_mem.mem_rd
  mem_wb.mem_pc        := ex_mem.mem_pc
  mem_wb.mem_opcode    := mem_opcode
  mem_wb.mem_inst      := mem_inst

  // WB Stage
  regfile.rd_addr    := mem_wb.wb_rd
  regfile.write_data := mem_wb.wb_wb_data
  regfile.rd_we      := mem_wb.wb_reg_write && (mem_wb.wb_rd =/= 0.U)

  // PC Update
  next_pc := MuxCase(
    pc + 4.U,
    Seq(
      id_branch_taken -> (id_pc + id_imm),
      id_is_jal       -> (id_pc + id_imm),
      id_is_jalr      -> ((id_rs1_data + id_imm) & "hfffffffe".U)
    )
  )

  when(!stall) {
    pc := next_pc
  }

  // Debug
  debug_pc        := mem_wb.wb_pc
  debug_inst      := mem_wb.wb_inst
  debug_reg_write := mem_wb.wb_reg_write && (mem_wb.wb_rd =/= 0.U)
  debug_reg_addr  := mem_wb.wb_rd
  debug_reg_data  := mem_wb.wb_wb_data
}

object RV32CPU extends App {
  VerilogEmitter.parse(new RV32CPU, "rv32_cpu.sv", info = true, lowering = true)
}
