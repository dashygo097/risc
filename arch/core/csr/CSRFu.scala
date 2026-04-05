package arch.core.csr

import arch.core.ooo._
import arch.core.decoder._
import arch.core.imm._
import arch.configs._
import chisel3._

class CsrFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_csr_fu"

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
