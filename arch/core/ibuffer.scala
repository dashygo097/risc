package arch.core

import arch.configs._
import mem.fifo._
import chisel3._
import chisel3.util._

class IBufferEntry(implicit p: Parameters) extends Bundle {
  val pc    = UInt(p(XLen).W)
  val instr = UInt(p(XLen).W)
}

class IBuffer(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_ibuffer"

  val enq   = IO(Flipped(Decoupled(new IBufferEntry)))
  val deq   = IO(Decoupled(new IBufferEntry))
  val empty = IO(Output(Bool()))
  val full  = IO(Output(Bool()))

  val fifo = Module(new SyncFIFO(new IBufferEntry, p(IBufferSize)))

  fifo.io.enq <> enq
  fifo.io.deq <> deq
  empty := fifo.io.empty
  full  := fifo.io.full
}
