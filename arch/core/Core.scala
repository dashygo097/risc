package arch.core

import decoder._
import imm._
import bru._
import alu._
import regfile._
import lsu._
import arch.configs._
import vopts.mem.cache._
import chisel3._
import chisel3.util._

class IBufferEntry(implicit p: Parameters) extends Bundle {
  val pc    = UInt(p(XLen).W)
  val instr = UInt(p(ILen).W)
}

class RiscCore(implicit p: Parameters) extends Module with ForwardingConsts with AluConsts {
  override def desiredName: String = s"${p(ISA)}_cpu"

  val decoder_utils = DecoderUtilitiesFactory.getOrThrow(p(ISA))
  val regfile_utils = RegfileUtilitiesFactory.getOrThrow(p(ISA))
  val bru_utils     = BruUtilitiesFactory.getOrThrow(p(ISA))
  val alu_utils     = AluUtilitiesFactory.getOrThrow(p(ISA))
  val lsu_utils     = LsuUtilitiesFactory.getOrThrow(p(ISA))

  val imem = IO(new UnifiedMemoryIO(p(XLen), p(XLen), 1, 1))
  val dmem = IO(new UnifiedMemoryIO(p(XLen), p(XLen), 1, 1))

  // Modules
  val decoder = Module(new Decoder)
  val ibuffer = Module(new Queue(new IBufferEntry, p(IBufferSize)))
  val bru     = Module(new Bru)
  val regfile = Module(new Regfile)
  val id_fwd  = Module(new IDForwardingUnit)
  val ex_fwd  = Module(new EXForwardingUnit)
  val imm_gen = Module(new ImmGen)
  val alu     = Module(new Alu)
  val lsu     = Module(new Lsu)

  // Pipelines
  val if_id  = Module(new IF_ID)
  val id_ex  = Module(new ID_EX)
  val ex_mem = Module(new EX_MEM)
  val mem_wb = Module(new MEM_WB)

  // Control Signals
  val pc = RegInit(0.U(p(XLen).W))

  val reset_ibuffer = RegInit(false.B)
  val ibuffer_empty = ibuffer.io.count === 0.U
  val ibuffer_full  = ibuffer.io.count === p(IBufferSize).U

  val imem_pending = RegInit(false.B)
  val imem_data    = RegInit(decoder_utils.bubble.value.U(p(ILen).W))
  val imem_pc      = RegInit(0.U(p(XLen).W))
  val imem_valid   = RegInit(false.B)

  val load_use_hazard = Wire(Bool())

  // IF Stage
  imem.req.valid     := !imem_pending && !ibuffer_full
  imem.req.bits.op   := MemoryOp.READ
  imem.req.bits.addr := pc
  imem.req.bits.data := 0.U(p(XLen).W)
  imem.resp.ready    := true.B

  when(imem.req.fire) {
    imem_pending := true.B
    imem_pc      := pc
    imem_valid   := true.B
  }

  when(bru.taken) {
    imem_valid := false.B
  }

  when(imem.resp.fire) {
    imem_data    := imem.resp.bits.data
    imem_pending := false.B
  }

  reset_ibuffer := false.B
  when(reset_ibuffer) {
    imem_valid := false.B
  }
  when(bru.taken) {
    reset_ibuffer := true.B
  }
  when(ibuffer_empty) {
    reset_ibuffer := false.B
  }

  ibuffer.io.enq.valid      := imem.resp.fire && imem_valid && !ibuffer_full
  ibuffer.io.enq.bits.pc    := imem_pc
  ibuffer.io.enq.bits.instr := imem.resp.bits.data

  ibuffer.io.deq.ready := (!ibuffer_empty && !if_id.STALL && !if_id.FLUSH) || reset_ibuffer

  // IF/ID Pipeline
  if_id.STALL    := id_ex.STALL || load_use_hazard
  if_id.FLUSH    := (bru.taken || !imem_valid || reset_ibuffer) && !lsu.busy
  if_id.IF_INSTR := Mux(ibuffer.io.deq.fire, ibuffer.io.deq.bits.instr, decoder_utils.bubble.value.U(p(ILen).W))
  if_id.IF.pc    := Mux(ibuffer.io.deq.fire, ibuffer.io.deq.bits.pc, decoder_utils.bubble.value.U(p(XLen).W))

  // ID Stage
  decoder.instr := if_id.ID_INSTR

  imm_gen.instr   := if_id.ID_INSTR
  imm_gen.immType := decoder.decoded.imm_type

  val rs1 = regfile_utils.getRs1(if_id.ID_INSTR)
  val rs2 = regfile_utils.getRs2(if_id.ID_INSTR)
  val rd  = regfile_utils.getRd(if_id.ID_INSTR)

  regfile.rs1_addr := rs1
  regfile.rs2_addr := rs2

  // ID Forwarding
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

  // BRU
  bru.en     := decoder.decoded.branch
  bru.pc     := if_id.ID.pc
  bru.src1   := id_rs1_data
  bru.src2   := id_rs2_data
  bru.imm    := imm_gen.imm
  bru.brType := decoder.decoded.br_type

  // Load-Use Hazard
  load_use_hazard := lsu_utils.isMemRead(id_ex.EX.decoded_output.lsu, id_ex.EX.decoded_output.lsu_cmd) &&
    ((id_ex.EX.rd === rs1) || (id_ex.EX.rd === rs2)) &&
    id_ex.EX.rd =/= 0.U

  // ID/EX Pipeline
  id_ex.STALL             := ex_mem.STALL
  id_ex.FLUSH             := (load_use_hazard || (bru.taken && !bru.jump)) && !lsu.busy
  id_ex.ID.decoded_output := decoder.decoded
  id_ex.ID_INSTR          := if_id.ID_INSTR
  id_ex.ID.pc             := if_id.ID.pc
  id_ex.ID.rd             := rd
  id_ex.ID.rs1            := rs1
  id_ex.ID.rs1_data       := id_rs1_data
  id_ex.ID.rs2            := rs2
  id_ex.ID.rs2_data       := id_rs2_data
  id_ex.ID.imm            := imm_gen.imm

  // EX Stage
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
      A2_IMM.value.U(SZ_A2.W)  -> id_ex.EX.imm,
      A2_FOUR.value.U(SZ_A2.W) -> 4.U(p(XLen).W)
    )
  )

  alu.en     := id_ex.EX.decoded_output.alu
  alu.src1   := alu_rs1_data
  alu.src2   := alu_rs2_data
  alu.fnType := id_ex.EX.decoded_output.alu_fn
  alu.mode   := id_ex.EX.decoded_output.alu_mode

  // EX/MEM Pipeline
  ex_mem.STALL         := mem_wb.STALL || lsu.busy
  ex_mem.FLUSH         := false.B
  ex_mem.EX.alu_result := alu.result
  ex_mem.EX_INSTR      := id_ex.EX_INSTR
  ex_mem.EX.pc         := id_ex.EX.pc
  ex_mem.EX.rd         := id_ex.EX.rd
  ex_mem.EX.rs2_data   := ex_rs2_data
  ex_mem.EX.regwrite   := id_ex.EX.decoded_output.regwrite
  ex_mem.EX.lsu        := id_ex.EX.decoded_output.lsu
  ex_mem.EX.lsu_cmd    := id_ex.EX.decoded_output.lsu_cmd

  // MEM Stage
  lsu.en    := ex_mem.MEM.lsu
  lsu.cmd   := ex_mem.MEM.lsu_cmd
  lsu.addr  := ex_mem.MEM.alu_result
  lsu.wdata := ex_mem.MEM.rs2_data

  dmem <> lsu.mem

  val mem_wb_data = Mux(
    lsu.mem_read,
    lsu.rdata,
    ex_mem.MEM.alu_result
  )

  // MEM/WB Pipeline
  mem_wb.STALL        := false.B
  mem_wb.FLUSH        := lsu.busy
  mem_wb.MEM_INSTR    := ex_mem.MEM_INSTR
  mem_wb.MEM.pc       := ex_mem.MEM.pc
  mem_wb.MEM.rd       := ex_mem.MEM.rd
  mem_wb.MEM.regwrite := ex_mem.MEM.regwrite
  mem_wb.MEM.wb_data  := mem_wb_data

  // WB Stage
  regfile.write_addr := mem_wb.WB.rd
  regfile.write_data := mem_wb.WB.wb_data
  regfile.write_en   := mem_wb.WB.regwrite

  // PC Update Logic
  when(bru.taken) {
    pc := bru.target
  }.elsewhen(ibuffer.io.enq.fire) {
    pc := pc + 4.U(p(XLen).W)
  }

  // Debug
  if (p(IsDebug)) {
    val debug_pc       = IO(Output(UInt(p(XLen).W)))
    val debug_instr    = IO(Output(UInt(p(ILen).W)))
    val debug_reg_we   = IO(Output(Bool()))
    val debug_reg_addr = IO(Output(UInt(regfile_utils.width.W)))
    val debug_reg_data = IO(Output(UInt(p(XLen).W)))

    val debug_if_instr  = IO(Output(UInt(p(ILen).W)))
    val debug_id_instr  = IO(Output(UInt(p(ILen).W)))
    val debug_ex_instr  = IO(Output(UInt(p(ILen).W)))
    val debug_mem_instr = IO(Output(UInt(p(ILen).W)))
    val debug_wb_instr  = IO(Output(UInt(p(ILen).W)))

    debug_pc       := mem_wb.WB.pc
    debug_instr    := mem_wb.WB_INSTR
    debug_reg_addr := regfile.write_addr
    debug_reg_we   := regfile.write_en
    debug_reg_data := regfile.write_data

    debug_if_instr  := Mux(ibuffer.io.deq.fire && !reset_ibuffer, ibuffer.io.deq.bits.instr, decoder_utils.bubble.value.U(p(ILen).W))
    debug_id_instr  := if_id.ID_INSTR
    debug_ex_instr  := id_ex.EX_INSTR
    debug_mem_instr := ex_mem.MEM_INSTR
    debug_wb_instr  := mem_wb.WB_INSTR
  }
}
