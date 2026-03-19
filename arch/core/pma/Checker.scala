package arch.core.pma

import arch.configs.proto.DeviceType._
import arch.configs._
import chisel3._

class PmaChecker(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_pma_checker"

  val addr      = IO(Input(UInt(p(XLen).W)))
  val valid     = IO(Output(Bool()))
  val readable  = IO(Output(Bool()))
  val writable  = IO(Output(Bool()))
  val cacheable = IO(Output(Bool()))
  val is_mmio   = IO(Output(Bool()))

  val hits = p(BusAddressMap).map { d =>
    val hit = (addr >= d.base.U(p(XLen).W)) && (addr < (d.base + d.size).U(p(XLen).W))
    (d.`type`, hit)
  }

  val is_sram = hits.filter(_._1 == DEVICE_TYPE_SRAM).map(_._2).reduce(_ || _)
  val is_uart = hits.filter(_._1 == DEVICE_TYPE_UART).map(_._2).reduce(_ || _)

  valid     := is_sram || is_uart
  readable  := is_sram || is_uart
  writable  := is_sram || is_uart
  cacheable := is_sram
  is_mmio   := is_uart
}

object PmaChecker {
  def apply(addr: UInt)(implicit p: Parameters): (Bool, Bool, Bool, Bool, Bool) = {
    val pma = Module(new PmaChecker)
    pma.addr := addr
    (pma.valid, pma.readable, pma.writable, pma.cacheable, pma.is_mmio)
  }
}
