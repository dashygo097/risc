package arch.core.ooo

import arch.configs._
import arch.core.alu.{ Alu, AluConsts }
import arch.core.mult.Mult
import arch.core.lsu.Lsu
import arch.core.csr.{ CsrFile, CsrUtilitiesFactory, CoreInterruptIO }
import arch.core.decoder.Decoder
import arch.core.imm.ImmUtilitiesFactory
import arch.core.pma.PmaChecker
import vopts.mem.cache.CacheIO
import chisel3._
import chisel3.util._

// --- ALU Wrapper ---
class AluFU(implicit p: Parameters) extends FunctionalUnit with AluConsts {
  val core_alu = Module(new Alu)
  val decoder  = Module(new Decoder)

  val imm_utils = ImmUtilitiesFactory.getOrThrow(p(ISA).name)

  val req_reg = Reg(new MicroOp)
  val valid   = RegInit(false.B)

  io.req.ready := !valid || io.resp.fire

  when(io.req.fire) {
    valid   := true.B
    req_reg := io.req.bits
  }.elsewhen(io.resp.fire || io.flush) {
    valid := false.B
  }

  decoder.instr := req_reg.instr

  val src1 = MuxLookup(decoder.decoded.alu_sel1, 0.U(p(XLen).W))(
    Seq(
      A1_ZERO.value.U(SZ_A1.W) -> 0.U(p(XLen).W),
      A1_RS1.value.U(SZ_A1.W)  -> req_reg.rs1_data,
      A1_PC.value.U(SZ_A1.W)   -> req_reg.pc
    )
  )

  val src2 = MuxLookup(decoder.decoded.alu_sel2, 0.U(p(XLen).W))(
    Seq(
      A2_ZERO.value.U(SZ_A2.W)   -> 0.U(p(XLen).W),
      A2_RS2.value.U(SZ_A2.W)    -> req_reg.rs2_data,
      A2_IMM.value.U(SZ_A2.W)    -> imm_utils.genImm(req_reg.instr, decoder.decoded.imm_type),
      A2_PCSTEP.value.U(SZ_A2.W) -> p(IAlign).U(p(XLen).W)
    )
  )

  core_alu.en     := valid
  core_alu.src1   := src1
  core_alu.src2   := src2
  core_alu.fnType := decoder.decoded.alu_fn
  core_alu.mode   := decoder.decoded.alu_mode

  io.resp.valid        := valid
  io.resp.bits.result  := core_alu.result
  io.resp.bits.rd      := req_reg.rd
  io.resp.bits.pc      := req_reg.pc
  io.resp.bits.instr   := req_reg.instr
  io.resp.bits.rob_tag := req_reg.rob_tag
}

// --- MULT Wrapper ---
class MultFU(implicit p: Parameters) extends FunctionalUnit {
  val core_mult = Module(new Mult)
  val decoder   = Module(new Decoder)

  val req_reg                                       = Reg(new MicroOp)
  val state_idle :: state_busy :: state_done :: Nil = Enum(3)
  val state                                         = RegInit(state_idle)

  io.req.ready := (state === state_idle) || (state === state_done && io.resp.fire)

  when(io.req.fire) {
    state   := state_busy
    req_reg := io.req.bits
  }.elsewhen(io.flush) {
    state := state_idle
  }.otherwise {
    when(state === state_busy && core_mult.io.done) {
      state := state_done
    }.elsewhen(state === state_done && io.resp.fire) {
      state := state_idle
    }
  }

  val current_instr = Mux(io.req.fire, io.req.bits.instr, req_reg.instr)
  decoder.instr := current_instr

  core_mult.io.en       := io.req.fire
  core_mult.io.kill     := io.flush
  core_mult.io.src1     := Mux(io.req.fire, io.req.bits.rs1_data, req_reg.rs1_data)
  core_mult.io.src2     := Mux(io.req.fire, io.req.bits.rs2_data, req_reg.rs2_data)
  core_mult.io.a_signed := decoder.decoded.mult_a_signed
  core_mult.io.b_signed := decoder.decoded.mult_b_signed
  core_mult.io.high     := decoder.decoded.mult_high

  io.resp.valid        := (state === state_done)
  io.resp.bits.result  := core_mult.io.result
  io.resp.bits.rd      := req_reg.rd
  io.resp.bits.pc      := req_reg.pc
  io.resp.bits.instr   := req_reg.instr
  io.resp.bits.rob_tag := req_reg.rob_tag
}

// --- LSU Wrapper ---
class LsuFU(implicit p: Parameters) extends FunctionalUnit {
  val mem  = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))
  val mmio = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))

  val core_lsu  = Module(new Lsu)
  val decoder   = Module(new Decoder)
  val imm_utils = ImmUtilitiesFactory.getOrThrow(p(ISA).name)

  val req_reg                                            = Reg(new MicroOp)
  val state_idle :: state_wait_resp :: state_done :: Nil = Enum(3)
  val state                                              = RegInit(state_idle)

  io.req.ready := (state === state_idle) || (state === state_done && io.resp.fire)

  when(io.req.fire) {
    state   := state_wait_resp
    req_reg := io.req.bits
  }.elsewhen(io.flush) {
    state := state_idle
  }.otherwise {
    when(state === state_wait_resp) {
      val resp_fired = core_lsu.mem.resp.fire || core_lsu.mmio.resp.fire
      when(resp_fired) {
        state := state_done
      }
    }.elsewhen(state === state_done && io.resp.fire) {
      state := state_idle
    }
  }

  core_lsu.en := (state === state_wait_resp)

  decoder.instr := req_reg.instr

  val imm                                            = imm_utils.genImm(req_reg.instr, decoder.decoded.imm_type)
  val addr                                           = req_reg.rs1_data + imm
  val (_, pma_readable, pma_writable, pma_cacheable) = PmaChecker(addr)

  core_lsu.cmd           := decoder.decoded.lsu_cmd
  core_lsu.addr          := addr
  core_lsu.wdata         := req_reg.rs2_data
  core_lsu.pma_readable  := pma_readable
  core_lsu.pma_writable  := pma_writable
  core_lsu.pma_cacheable := pma_cacheable

  mem <> core_lsu.mem
  mmio <> core_lsu.mmio

  io.resp.valid        := (state === state_done)
  io.resp.bits.result  := core_lsu.rdata
  io.resp.bits.rd      := req_reg.rd
  io.resp.bits.pc      := req_reg.pc
  io.resp.bits.instr   := req_reg.instr
  io.resp.bits.rob_tag := req_reg.rob_tag
}

// --- CSR Wrapper ---
class CsrFU(implicit p: Parameters) extends FunctionalUnit {
  val trap_request = IO(Output(Bool()))
  val trap_target  = IO(Output(UInt(p(XLen).W)))
  val trap_ret_tgt = IO(Output(UInt(p(XLen).W)))
  val irq          = IO(Input(new CoreInterruptIO))
  val cycle        = IO(Input(UInt(64.W)))
  val instret      = IO(Input(UInt(64.W)))

  val core_csr  = Module(new CsrFile)
  val decoder   = Module(new Decoder)
  val imm_utils = ImmUtilitiesFactory.getOrThrow(p(ISA).name)
  val csr_utils = CsrUtilitiesFactory.getOrThrow(p(ISA).name)

  val req_reg = Reg(new MicroOp)
  val valid   = RegInit(false.B)

  io.req.ready := !valid || io.resp.fire

  when(io.req.fire) {
    valid   := true.B
    req_reg := io.req.bits
  }.elsewhen(io.resp.fire || io.flush) {
    valid := false.B
  }

  decoder.instr := req_reg.instr

  val csr_imm  = imm_utils.genCsrImm(req_reg.instr)
  val csr_addr = csr_utils.getAddr(req_reg.instr)

  core_csr.en       := valid && !core_csr.trap_request
  core_csr.cmd      := decoder.decoded.csr_cmd
  core_csr.addr     := csr_addr
  core_csr.imm      := csr_imm
  core_csr.src      := req_reg.rs1_data
  core_csr.pc       := req_reg.pc
  core_csr.trap_ret := decoder.decoded.ret

  if (core_csr.extraInputIO.contains("cycle")) core_csr.extraInputIO("cycle")         := cycle
  if (core_csr.extraInputIO.contains("instret")) core_csr.extraInputIO("instret")     := instret
  if (core_csr.extraInputIO.contains("timer_irq")) core_csr.extraInputIO("timer_irq") := irq.timer_irq
  if (core_csr.extraInputIO.contains("soft_irq")) core_csr.extraInputIO("soft_irq")   := irq.soft_irq
  if (core_csr.extraInputIO.contains("ext_irq")) core_csr.extraInputIO("ext_irq")     := irq.ext_irq

  trap_request := core_csr.trap_request
  trap_target  := core_csr.trap_target
  trap_ret_tgt := core_csr.trap_ret_target

  io.resp.valid        := valid
  io.resp.bits.result  := core_csr.rd
  io.resp.bits.rd      := req_reg.rd
  io.resp.bits.pc      := req_reg.pc
  io.resp.bits.instr   := req_reg.instr
  io.resp.bits.rob_tag := req_reg.rob_tag
}
