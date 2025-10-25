package core 

import chisel3._

class InstMem extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(32.W))
    val inst = Output(UInt(32.W))
  })
  val mem = Mem(256, UInt(32.W))
  // loadMemoryFromFile(mem, "inst_mem_init.hex")
  io.inst := mem(io.addr(9, 2)) // word-aligned addresses
}
