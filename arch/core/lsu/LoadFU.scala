package arch.core.lsu

import arch.core.ooo._
import arch.core.imm._
import arch.core.pma._
import arch.configs._
import vopts.mem.cache._
import chisel3._
import chisel3.util._

object LoadFUState extends ChiselEnum {
  val IDLE, CHECK_SB, SEND_MEM, WAIT_MEM, DONE, FLUSH_DRAIN = Value
}

class LoadCtrl(implicit p: Parameters) extends Bundle {
  val is_byte     = Bool()
  val is_half     = Bool()
  val is_word     = Bool()
  val is_dword    = Bool()
  val is_unsigned = Bool()
  val strb        = UInt(p(BytesPerWord).W)
}

class LoadFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_load_fu"

  val mem  = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))
  val mmio = IO(new CacheIO(UInt(p(XLen).W), p(XLen)))

  val sbFwd = IO(Flipped(new StoreForwardPort))
  val busy  = IO(Output(Bool()))

  val utils    = LoadUtilsFactory.getOrThrow(p(ISA).name)
  val immUtils = ImmUtilsFactory.getOrThrow(p(ISA).name)

  val state     = RegInit(LoadFUState.IDLE)
  val uopReg    = Reg(new MicroOp)
  val resultReg = RegInit(0.U(p(XLen).W))

  val fwdDataReg = RegInit(0.U(p(XLen).W))
  val fwdMaskReg = RegInit(0.U(p(BytesPerWord).W))

  val reqOutstanding = RegInit(false.B)
  val reqWasCache    = RegInit(false.B)

  val ctrl = utils.decodeLoad(uopReg.uop)
  val imm  = immUtils.genImm(uopReg.instr, uopReg.imm_type)
  val addr = uopReg.rs1_data + imm

  val alignedAddr = utils.alignedAddr(addr)
  val loadMask    = utils.shiftedLoadMask(ctrl, addr)

  val (_, pmaReadable, _, pmaCacheable) = PmaChecker(addr)

  busy := state =/= LoadFUState.IDLE

  io.req.ready := state === LoadFUState.IDLE

  sbFwd.req.valid  := state === LoadFUState.CHECK_SB
  sbFwd.req.sq_seq := uopReg.sq_seq
  sbFwd.req.addr   := alignedAddr
  sbFwd.req.mask   := loadMask

  val fwdResp = sbFwd.resp

  val mmioOrderBlock = !pmaCacheable && fwdResp.hasOlder
  val shouldBlock    = fwdResp.block || mmioOrderBlock
  val fullForward    = pmaCacheable && fwdResp.fwdFull
  val partialForward = pmaCacheable && fwdResp.fwdValid && !fwdResp.fwdFull

  mem.req.valid     := state === LoadFUState.SEND_MEM && pmaCacheable
  mem.req.bits.op   := CacheOp.READ
  mem.req.bits.addr := alignedAddr
  mem.req.bits.data := 0.U
  mem.req.bits.strb := loadMask

  mmio.req.valid     := state === LoadFUState.SEND_MEM && !pmaCacheable
  mmio.req.bits.op   := CacheOp.READ
  mmio.req.bits.addr := alignedAddr
  mmio.req.bits.data := 0.U
  mmio.req.bits.strb := loadMask

  mem.resp.ready  := (state === LoadFUState.WAIT_MEM || state === LoadFUState.FLUSH_DRAIN) && reqWasCache
  mmio.resp.ready := (state === LoadFUState.WAIT_MEM || state === LoadFUState.FLUSH_DRAIN) && !reqWasCache

  val memReqFire  = mem.req.fire || mmio.req.fire
  val memRespFire = mem.resp.fire || mmio.resp.fire
  val memRespData = Mux(reqWasCache, mem.resp.bits.data, mmio.resp.bits.data)

  val expandedFwdMask = utils.expandByteMask(fwdMaskReg)
  val mergedBusData   = (memRespData & ~expandedFwdMask) | (fwdDataReg & expandedFwdMask)

  io.resp.valid        := state === LoadFUState.DONE
  io.resp.bits.result  := resultReg
  io.resp.bits.rd      := uopReg.rd
  io.resp.bits.pc      := uopReg.pc
  io.resp.bits.instr   := uopReg.instr
  io.resp.bits.rob_tag := uopReg.rob_tag

  when(memReqFire) {
    reqOutstanding := true.B
    reqWasCache    := pmaCacheable
  }

  when(memRespFire) {
    reqOutstanding := false.B
  }

  val willHaveOutstanding = (reqOutstanding || memReqFire) && !memRespFire

  when(io.flush) {
    when(willHaveOutstanding) {
      state := LoadFUState.FLUSH_DRAIN
    }.otherwise {
      state := LoadFUState.IDLE
    }
  }.otherwise {
    switch(state) {
      is(LoadFUState.IDLE) {
        when(io.req.fire) {
          uopReg     := io.req.bits
          fwdDataReg := 0.U
          fwdMaskReg := 0.U
          state      := LoadFUState.CHECK_SB
        }
      }

      is(LoadFUState.CHECK_SB) {
        when(!shouldBlock) {
          when(fullForward) {
            resultReg := utils.loadResult(ctrl, addr, fwdResp.fwdData)
            state     := LoadFUState.DONE
          }.otherwise {
            fwdDataReg := Mux(partialForward, fwdResp.fwdData, 0.U)
            fwdMaskReg := Mux(partialForward, fwdResp.fwdMask, 0.U)
            state      := LoadFUState.SEND_MEM
          }
        }
      }

      is(LoadFUState.SEND_MEM) {
        when(memReqFire) {
          state := LoadFUState.WAIT_MEM
        }
      }

      is(LoadFUState.WAIT_MEM) {
        when(memRespFire) {
          resultReg := utils.loadResult(ctrl, addr, mergedBusData)
          state     := LoadFUState.DONE
        }
      }

      is(LoadFUState.DONE) {
        when(io.resp.fire) {
          state := LoadFUState.IDLE
        }
      }

      is(LoadFUState.FLUSH_DRAIN) {
        when(memRespFire) {
          state := LoadFUState.IDLE
        }
      }
    }
  }
}
