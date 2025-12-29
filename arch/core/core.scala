package arch.core

import decoder._
import imm._
import alu._
import regfile._
import lsu._
import arch.configs._
import mem.cache._
import chisel3._
import chisel3.util._

class BranchComparator(implicit p: Parameters) extends Module {
  val src1   = IO(Input(UInt(p(XLen).W)))
  val src2   = IO(Input(UInt(p(XLen).W)))
  val fnType = IO(Input(UInt(4.W)))
  val enable = IO(Input(Bool()))
  val taken  = IO(Output(Bool()))

  val eq  = src1 === src2
  val lt  = src1.asSInt < src2.asSInt
  val ltu = src1 < src2

  taken := enable && MuxLookup(fnType(2, 0), false.B)(
    Seq(
      0.U -> !eq, // SNE
      1.U -> eq,  // SEQ
      2.U -> lt,  // SLT
      3.U -> ltu, // SLTU
      4.U -> !lt, // SGE
      5.U -> !ltu // SGEU
    )
  )
}

class RiscCore(implicit p: Parameters) extends Module with ForwardingConsts with AluConsts {
  override def desiredName: String = s"${p(ISA)}_cpu"

  val regfile_utils = RegfileUtilitiesFactory.getOrThrow(p(ISA))
  val alu_utils     = AluUtilitiesFactory.getOrThrow(p(ISA))
  val lsu_utils     = LsuUtilitiesFactory.getOrThrow(p(ISA))

  val imem = IO(new UnifiedMemoryIO(p(XLen), p(XLen), 1, 1))
  val dmem = IO(new UnifiedMemoryIO(p(XLen), p(XLen), 1, 1))

  // Debug
  val debug_pc       = IO(Output(UInt(p(XLen).W)))
  val debug_instr    = IO(Output(UInt(p(ILen).W)))
  val debug_reg_we   = IO(Output(Bool()))
  val debug_reg_addr = IO(Output(UInt(regfile_utils.width.W)))
  val debug_reg_data = IO(Output(UInt(p(XLen).W)))

  // Modules
  val decoder     = Module(new Decoder)
  val regfile     = Module(new Regfile)
  val id_fwd      = Module(new IDForwardingUnit)
  val ex_fwd      = Module(new EXForwardingUnit)
  val imm_gen     = Module(new ImmGen)
  val alu         = Module(new Alu)
  val branch_comp = Module(new BranchComparator)

  // Pipelines
  val if_id  = Module(new IF_ID)
  val id_ex  = Module(new ID_EX)
  val ex_mem = Module(new EX_MEM)
  val mem_wb = Module(new MEM_WB)

  // Control Signals
  val stall = Wire(Bool())
  val flush = Wire(Bool())

  // IF
  val pc         = RegInit(0.U(p(XLen).W))
  val next_pc    = Wire(UInt(p(XLen).W))
  val pc_updated = RegInit(false.B)

  val imem_pending = RegInit(false.B)
  val imem_data    = RegInit(0.U(p(ILen).W))

  imem.req.valid     := !imem_pending && !stall && !flush
  imem.req.bits.op   := MemoryOp.READ
  imem.req.bits.addr := pc
  imem.req.bits.data := DontCare
  imem.resp.ready    := true.B

  when(imem.req.fire && !flush) {
    imem_pending := true.B
  }

  when(imem.resp.fire) {
    imem_data    := imem.resp.bits.data
    imem_pending := false.B
  }

  when(flush) {
    imem_pending := false.B
  }

  // IF/ID
  if_id.STALL    := stall || imem_pending
  if_id.FLUSH    := flush
  if_id.IF.pc    := pc
  if_id.IF.instr := Mux(imem.resp.fire, imem.resp.bits.data, imem_data)

  // ID
  decoder.instr := if_id.ID.instr

  val rs1 = if_id.ID.instr(19, 15)
  val rs2 = if_id.ID.instr(24, 20)
  val rd  = if_id.ID.instr(11, 7)

  regfile.rs1_addr := rs1
  regfile.rs2_addr := rs2

  id_fwd.id_rs1       := rs1
  id_fwd.id_rs2       := rs2
  id_fwd.ex_rd        := id_ex.EX.rd
  id_fwd.ex_regwrite  := id_ex.EX.decoded_output.regwrite
  id_fwd.mem_rd       := ex_mem.MEM.rd
  id_fwd.mem_regwrite := ex_mem.MEM.regwrite
  id_fwd.wb_rd        := mem_wb.WB.rd
  id_fwd.wb_regwrite  := mem_wb.WB.regwrite

  val id_rs1_data = MuxLookup(id_fwd.forward_rs1, 0.U(p(XLen).W))(
    Seq(
      FWD_SAFE.value.U(SZ_FWD.W) -> regfile.rs1_data,
      FWD_EX.value.U(SZ_FWD.W)   -> alu.result,
      FWD_MEM.value.U(SZ_FWD.W)  -> ex_mem.MEM.alu_result,
      FWD_WB.value.U(SZ_FWD.W)   -> mem_wb.WB.wb_data
    )
  )

  val id_rs2_data = MuxLookup(id_fwd.forward_rs2, 0.U(p(XLen).W))(
    Seq(
      FWD_SAFE.value.U(SZ_FWD.W) -> regfile.rs2_data,
      FWD_EX.value.U(SZ_FWD.W)   -> alu.result,
      FWD_MEM.value.U(SZ_FWD.W)  -> ex_mem.MEM.alu_result,
      FWD_WB.value.U(SZ_FWD.W)   -> mem_wb.WB.wb_data
    )
  )

  // branch
  val is_branch = decoder.decoded.alu && alu_utils.isComparison(decoder.decoded.alu_fn)
  val is_jal    = false.B // TODO: from decoder
  val is_jalr   = false.B // TODO: from decoder

  branch_comp.src1   := id_rs1_data
  branch_comp.src2   := id_rs2_data
  branch_comp.fnType := decoder.decoded.alu_fn
  branch_comp.enable := is_branch

  val branch_taken = branch_comp.taken
  val jump_taken   = is_jal || is_jalr

  // hazard detection
  val load_use_hazard = id_ex.EX.decoded_output.lsu &&
    lsu_utils.isMemRead(id_ex.EX.decoded_output.lsu, id_ex.EX.decoded_output.lsu_cmd) &&
    ((id_ex.EX.rd === rs1) || (id_ex.EX.rd === rs2)) &&
    (id_ex.EX.rd =/= 0.U)

  val branch_hazard = (is_branch || is_jalr) &&
    id_ex.EX.decoded_output.lsu &&
    lsu_utils.isMemRead(id_ex.EX.decoded_output.lsu, id_ex.EX.decoded_output.lsu_cmd) &&
    ((id_ex.EX.rd === rs1) || (id_ex.EX.rd === rs2)) &&
    (id_ex.EX.rd =/= 0.U)

  stall := load_use_hazard || branch_hazard
  flush := branch_taken || jump_taken

  // ID/EX
  id_ex.STALL             := stall
  id_ex.FLUSH             := flush || stall
  id_ex.ID.decoded_output := decoder.decoded
  id_ex.ID.instr          := if_id.ID.instr
  id_ex.ID.pc             := if_id.ID.pc
  id_ex.ID.rd             := rd
  id_ex.ID.rs1            := rs1
  id_ex.ID.rs1_data       := id_rs1_data
  id_ex.ID.rs2            := rs2
  id_ex.ID.rs2_data       := id_rs2_data

  // EX
  // Imm
  imm_gen.instr   := id_ex.EX.instr
  imm_gen.immType := id_ex.EX.decoded_output.imm_type

  ex_fwd.ex_rs1       := id_ex.EX.rs1
  ex_fwd.ex_rs2       := id_ex.EX.rs2
  ex_fwd.mem_rd       := ex_mem.MEM.rd
  ex_fwd.mem_regwrite := ex_mem.MEM.regwrite
  ex_fwd.wb_rd        := mem_wb.WB.rd
  ex_fwd.wb_regwrite  := mem_wb.WB.regwrite

  val ex_rs1_data = MuxLookup(ex_fwd.forward_rs1, 0.U(p(XLen).W))(
    Seq(
      FWD_SAFE.value.U(SZ_FWD.W) -> id_ex.EX.rs1_data,
      FWD_MEM.value.U(SZ_FWD.W)  -> ex_mem.MEM.alu_result,
      FWD_WB.value.U(SZ_FWD.W)   -> mem_wb.WB.wb_data
    )
  )

  val ex_rs2_data = MuxLookup(ex_fwd.forward_rs2, 0.U(p(XLen).W))(
    Seq(
      FWD_SAFE.value.U(SZ_FWD.W) -> id_ex.EX.rs2_data,
      FWD_MEM.value.U(SZ_FWD.W)  -> ex_mem.MEM.alu_result,
      FWD_WB.value.U(SZ_FWD.W)   -> mem_wb.WB.wb_data
    )
  )

  // ALU
  val alu_rs1_data = MuxLookup(id_ex.EX.decoded_output.alu_sel1, 0.U(p(XLen).W))(
    Seq(
      A1_ZERO.value.U(SZ_A1.W) -> 0.U(p(XLen).W),
      A1_RS1.value.U(SZ_A1.W)  -> ex_rs1_data,
      A1_PC.value.U(SZ_A1.W)   -> id_ex.EX.pc
    )
  )

  val alu_rs2_data = MuxLookup(id_ex.EX.decoded_output.alu_sel2, 0.U(p(XLen).W))(
    Seq(
      A2_ZERO.value.U(SZ_A2.W) -> 0.U(p(XLen).W),
      A2_RS2.value.U(SZ_A2.W)  -> ex_rs2_data,
      A2_IMM.value.U(SZ_A2.W)  -> imm_gen.imm,
    )
  )

  alu.en     := id_ex.EX.decoded_output.alu
  alu.src1   := alu_rs1_data
  alu.src2   := alu_rs2_data
  alu.fnType := id_ex.EX.decoded_output.alu_fn
  alu.mode   := id_ex.EX.decoded_output.alu_mode

  // EX/MEM
  ex_mem.STALL := false.B
  ex_mem.FLUSH := false.B

  ex_mem.EX.alu_result := alu.result
  ex_mem.EX.instr      := id_ex.EX.instr
  ex_mem.EX.pc         := id_ex.EX.pc
  ex_mem.EX.rd         := id_ex.EX.rd
  ex_mem.EX.rs2_data   := ex_rs2_data
  ex_mem.EX.regwrite   := id_ex.EX.decoded_output.regwrite
  ex_mem.EX.lsu        := id_ex.EX.decoded_output.lsu
  ex_mem.EX.lsu_cmd    := id_ex.EX.decoded_output.lsu_cmd

  // MEM
  val dmem_state = RegInit(false.B)
  val dmem_data  = RegInit(0.U(p(XLen).W))

  val mem_read  = lsu_utils.isMemRead(ex_mem.MEM.lsu, ex_mem.MEM.lsu_cmd)
  val mem_write = lsu_utils.isMemWrite(ex_mem.MEM.lsu, ex_mem.MEM.lsu_cmd)

  dmem.req.valid     := (mem_read || mem_write) && !dmem_state
  dmem.req.bits.op   := Mux(mem_write, MemoryOp.WRITE, MemoryOp.READ)
  dmem.req.bits.addr := ex_mem.MEM.alu_result
  dmem.req.bits.data := ex_mem.MEM.rs2_data
  dmem.resp.ready    := true.B

  when(dmem.req.fire) {
    dmem_state := true.B
  }

  when(dmem.resp.fire) {
    dmem_data  := dmem.resp.bits.data
    dmem_state := false.B
  }

  val mem_wb_data = Mux(
    mem_read,
    Mux(dmem.resp.fire, dmem.resp.bits.data, dmem_data),
    ex_mem.MEM.alu_result
  )

  // MEM/WB
  mem_wb.STALL        := dmem_state
  mem_wb.FLUSH        := false.B
  mem_wb.MEM.wb_data  := mem_wb_data
  mem_wb.MEM.instr    := ex_mem.MEM.instr
  mem_wb.MEM.pc       := ex_mem.MEM.pc
  mem_wb.MEM.rd       := ex_mem.MEM.rd
  mem_wb.MEM.regwrite := ex_mem.MEM.regwrite

  // WB
  regfile.write_addr := mem_wb.WB.rd
  regfile.write_data := mem_wb.WB.wb_data
  regfile.write_en   := mem_wb.WB.regwrite

  // pc update
  val branch_target = if_id.ID.pc + imm_gen.imm
  val jalr_target   = (id_rs1_data + imm_gen.imm) & ~1.U(p(XLen).W)

  // TODO: add branch and jar/jalr targets
  next_pc := pc + 4.U

  when(!stall && !imem_pending) {
    pc := next_pc
  }

  // debug ports
  debug_pc       := mem_wb.WB.pc
  debug_instr    := mem_wb.WB.instr
  debug_reg_addr := mem_wb.WB.rd
  debug_reg_we   := mem_wb.WB.regwrite
  debug_reg_data := mem_wb.WB.wb_data
}
