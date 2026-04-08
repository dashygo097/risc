ackage arch.core.bru
S
import arch.core.ooo._
import arch.core.decoder._
import arch.core.imm._
import arch.configs._
import chisel3._

class BruFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_bru_fu"

  val actual_taken  = IO(Output(Bool()))
  val actual_target = IO(Output(UInt(p(XLen).W)))

  val bru     = Module(new Bru)
  val decoder = Module(new Decoder)
  val imm_gen = Module(new ImmGen)

  val busy    = RegInit(false.B)
  val req_reg = Reg(new MicroOp)

  io.req.ready := !busy || io.resp.ready

  when(io.flush) {
    busy := false.B
  }.elsewhen(io.req.fire) {
    busy    := true.B
    req_reg := io.req.bits
  }.elsewhen(io.resp.fire) {
    busy := false.B
  }

  decoder.instr   := req_reg.instr
  imm_gen.instr   := req_reg.instr
  imm_gen.immType := decoder.decoded.imm_type

  bru.en     := busy && decoder.decoded.branch
  bru.pc     := req_reg.pc
  bru.src1   := req_reg.rs1_data
  bru.src2   := req_reg.rs2_data
  bru.imm    := imm_gen.imm
  bru.brType := decoder.decoded.br_type

  io.resp.valid        := busy && !io.flush
  io.resp.bits.pc      := req_reg.pc
  io.resp.bits.instr   := req_reg.instr
  io.resp.bits.rd      := req_reg.rd
  io.resp.bits.rob_tag := req_reg.rob_tag
  
  io.resp.bits.result  := req_reg.pc + p(IAlign).U 

  actual_taken  := bru.taken
  actual_target := bru.target
}
