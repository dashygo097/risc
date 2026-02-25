#include "demu/cpu_sim.hh"
#include "demu/elf_loader.hh"
#include "demu/logger.hh"
#include <iomanip>
#include <sstream>

namespace demu {
using namespace isa;

CPUSimulator::CPUSimulator(bool enable_trace)
    : dut_(std::make_unique<cpu_t>()),
      imem_(std::make_unique<hal::Memory>(4 * 1024, 0x00000000)),
      dmem_(std::make_unique<hal::Memory>(16 * 1024, 0x80000000)),
      trace_(std::make_unique<ExecutionTrace>()), trace_enabled_(enable_trace) {
#ifdef ENABLE_TRACE
  if (trace_enabled_) {
    Verilated::traceEverOn(true);
    vcd_ = std::make_unique<VerilatedVcdC>();
    dut_->trace(vcd_.get(), 99);
    vcd_->open((std::string(ISA_NAME) + "_cpu.vcd").c_str());
  }
#endif
}

CPUSimulator::~CPUSimulator() {
#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->close();
  }
#endif
}

bool CPUSimulator::load_bin(const std::string &filename, addr_t base_addr) {
  return imem_->load_binary(filename, base_addr);
}

bool CPUSimulator::load_elf(const std::string &filename) {
  return ELFLoader::load(*imem_, filename);
}

void CPUSimulator::reset() {
  DEMU_INFO("CPU Resetting...");
  dut_->reset = 1;
  dut_->clock = 0;

  dut_->imem_req_ready = 1;
  dut_->imem_resp_valid = 0;
  IMEM_CLEAR_RESP_DATA(dut_, 4)

  dut_->dmem_req_ready = 1;
  dut_->dmem_resp_valid = 0;
  DMEM_CLEAR_RESP_DATA(dut_, 4)

  dut_->eval();

  for (int i = 0; i < 5; i++) {
    dut_->clock = 1;
    dut_->eval();
    dut_->clock = 0;
    dut_->eval();
  }

  dut_->reset = 0;
  dut_->eval();

  instrCount = 0;
  terminate_ = false;
  _register_values.clear();
  trace_->clear();

  _imem_pending = 0;
  _dmem_pending = 0;

  _dmem_pending_data.resize(4);

  on_reset();
  DEMU_INFO("CPU Reset Complete. PC: 0x{:08x}",
            static_cast<addr_t>(dut_->debug_pc));
}

void CPUSimulator::handle_cache_profiling() {
  if (dut_->debug_l1_icache_access) {
    _l1_icache_accesses++;
    if (dut_->debug_l1_icache_miss) {
      _l1_icache_misses++;
    }
  }

  if (dut_->debug_l1_dcache_access) {
    _l1_dcache_accesses++;
    if (dut_->debug_l1_dcache_miss) {
      _l1_dcache_misses++;
    }
  }
}

void CPUSimulator::handle_imem_interface() {
  if (!_imem_pending) {
    dut_->imem_resp_valid = 0;
    IMEM_CLEAR_RESP_DATA(dut_, 4)

    if (dut_->imem_req_valid && dut_->imem_req_ready) {
      _imem_pending_addr = static_cast<addr_t>(dut_->imem_req_bits_addr);
      _imem_pending_latency = imem_delay_;
      _imem_pending = true;
      DEMU_MEM_TRACE("IFETCH", _imem_pending_addr, 0);
    }
  } else {
    _imem_pending_latency--;

    if (_imem_pending_latency > 0) {
      dut_->imem_resp_valid = 0;
      IMEM_CLEAR_RESP_DATA(dut_, 4)
    } else {
      word_t data_ptr[4];
      for (int i = 0; i < 4; i++) {
        addr_t addr = _imem_pending_addr + (i * 4);
        word_t data = imem_->read_word(addr);
        data_ptr[i] = data;
        DEMU_TRACE("[IMEM READ] addr=0x{:08x} data=0x{:08x}", addr, data);
      }
      IMEM_SET_RESP_DATA(dut_, data_ptr, 4)
      dut_->imem_resp_valid = 1;

      if (dut_->imem_resp_ready) {
        _imem_pending = false;
      }
    }
  }
}

void CPUSimulator::handle_dmem_interface() {
  if (!_dmem_pending) {
    dut_->dmem_resp_valid = 0;
    DMEM_CLEAR_RESP_DATA(dut_, 4)

    if (dut_->dmem_req_valid && dut_->dmem_req_ready) {
      _dmem_pending_addr = static_cast<addr_t>(dut_->dmem_req_bits_addr);
      _dmem_pending_op = dut_->dmem_req_bits_op;

      word_t data_ptr[4];
      DMEM_GET_REQ_DATA(dut_, data_ptr, 4)
      for (int i = 0; i < 4; i++) {
        _dmem_pending_data[i] = data_ptr[i];
      }
      _dmem_pending_latency = dmem_delay_;
      _dmem_pending = true;

      const char *op_name = _dmem_pending_op ? "WRITE" : "READ";
      DEMU_MEM_TRACE(op_name, _dmem_pending_addr, _dmem_pending_data[0]);
    }
  } else {
    _dmem_pending_latency--;

    if (_dmem_pending_latency > 0) {
      dut_->dmem_resp_valid = 0;
      DMEM_CLEAR_RESP_DATA(dut_, 4)
    } else {
      if (_dmem_pending_op) {
        for (int i = 0; i < (int)_dmem_pending_data.size(); i++) {
          addr_t addr = _dmem_pending_addr + (i * 4);
          dmem_->write_word(addr, _dmem_pending_data[i]);
          DEMU_TRACE("[DMEM WRITE] addr=0x{:08x} data=0x{:08x}", addr,
                     _dmem_pending_data[i]);
        }
        DMEM_CLEAR_RESP_DATA(dut_, 4)
      } else {
        word_t data_ptr[4];
        for (int i = 0; i < 4; i++) {
          addr_t addr = _dmem_pending_addr + (i * 4);
          word_t data = dmem_->read_word(addr);
          data_ptr[i] = data;
          DEMU_TRACE("[DMEM READ] addr=0x{:08x} data=0x{:08x}", addr, data);
        }
        DMEM_SET_RESP_DATA(dut_, data_ptr, 4)
      }

      dut_->dmem_resp_valid = 1;

      if (dut_->dmem_resp_ready) {
        _dmem_pending = false;
      }
    }
  }
}

void CPUSimulator::clock_tick() {
  DEMU_CPU_TICK(cycle_count());

  dut_->clock = 0;
  handle_imem_interface();
  handle_dmem_interface();
  dut_->eval();

#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->dump(timeCount++);
  }
#endif

  handle_cache_profiling();

  if (dut_->debug_reg_addr != 0) {
    if (dut_->debug_reg_we) {
      word_t reg_data = static_cast<word_t>(dut_->debug_reg_data);
      _register_values[dut_->debug_reg_addr] = reg_data;
      DEMU_REG_WRITE(dut_->debug_reg_addr, reg_data);
    }
    if (trace_enabled_) {
      TraceEntry entry;
      entry.cycle = cycle_count();
      entry.pc = static_cast<addr_t>(dut_->debug_pc);
      entry.inst = static_cast<instr_t>(dut_->debug_instr);
      entry.rd = dut_->debug_reg_addr;
      entry.rd_val = static_cast<word_t>(dut_->debug_reg_data);
      entry.regwrite = dut_->debug_reg_we;
      Instruction inst(entry.inst);
      entry.disasm = inst.to_string();
      trace_->add_entry(entry);
    }
  }

  if (show_pipeline_) {
    DEMU_DEBUG("PIPE | IF:{:08x} ID:{:08x} EX:{:08x} MEM:{:08x} WB:{:08x}",
               dut_->debug_if_instr, dut_->debug_id_instr, dut_->debug_ex_instr,
               dut_->debug_mem_instr, dut_->debug_wb_instr);
  }

  if (dut_->debug_branch_taken) {
    DEMU_TRACE("[BRANCH] Taken: 0x{:08x} -> 0x{:08x}",
               dut_->debug_branch_source, dut_->debug_branch_target);
  }

  if (dut_->debug_wb_instr != BUBBLE) {
    instrCount++;
    Instruction inst(static_cast<instr_t>(dut_->debug_wb_instr));
    DEMU_INFO("RETIRE | Cycle {:6d} | PC=0x{:08x} | Inst=0x{:08x} ({})",
              cycle_count(), static_cast<addr_t>(dut_->debug_pc),
              dut_->debug_wb_instr, inst.to_string());
  }

  on_clock_tick();

  dut_->clock = 1;
  dut_->eval();

#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->dump(timeCount++);
  }
#endif
}

void CPUSimulator::step(uint64_t cycles) {
  for (int i = 0; i < (int)cycles; i++) {
    clock_tick();
  }
}

void CPUSimulator::run(uint64_t max_cycles) {
  uint64_t target = max_cycles > 0 ? max_cycles : timeout_;

  auto start_time = std::chrono::high_resolution_clock::now();
  on_init();
  while (cycle_count() < target && !terminate_) {
    clock_tick();
    check_termination();
  }
  on_exit();
  auto end_time = std::chrono::high_resolution_clock::now();

  auto duration = std::chrono::duration_cast<std::chrono::microseconds>(
                      end_time - start_time)
                      .count();

  DEMU_INFO("Simulation completed: {} cycles, {} instructions, IPC: {:.2f}, "
            "after {} ms",
            cycle_count(), instr_count(), ipc(), duration / 1000.0);

  DEMU_INFO("L1 Icache Hit Rate: {:.2f} %", l1_icache_hit_rate() * 100);
  DEMU_INFO("L1 Dcache Hit Rate: {:.2f} %", l1_dcache_hit_rate() * 100);
}

word_t CPUSimulator::read_mem(addr_t addr) const {
  if (addr >= dmem_->base_address()) {
    return dmem_->read_word(addr);
  }
  return imem_->read_word(addr);
}

void CPUSimulator::write_mem(addr_t addr, word_t data) {
  if (addr >= dmem_->base_address()) {
    dmem_->write_word(addr, data);
  } else {
    imem_->write_word(addr, data);
  }
}

void CPUSimulator::dump_registers() const {
  DEMU_INFO("Register Dump:");
  for (int i = 0; i < NUM_GPRS; i += 4) {
    DEMU_INFO("x{:02d}={:08x}  x{:02d}={:08x}  x{:02d}={:08x}  x{:02d}={:08x}",
              i, reg(i), i + 1, reg(i + 1), i + 2, reg(i + 2), i + 3,
              reg(i + 3));
  }
}

void CPUSimulator::dump_memory(addr_t start, size_t size) const {
  DEMU_INFO("Memory dump [0x{:08x} - 0x{:08x}]:", start, start + size);
  for (addr_t addr = start; addr < start + size; addr += 16) {
    std::ostringstream line;
    line << std::hex << std::setw(8) << std::setfill('0') << addr << ": ";
    for (size_t i = 0; i < 16 && (addr + i) < (start + size); i += 4) {
      word_t word = read_mem(addr + i);
      for (int j = 0; j < 4 && (i + j) < 16; j++) {
        line << std::hex << std::setw(2) << std::setfill('0')
             << (int)((word >> (j * 8)) & 0xFF) << " ";
      }
      if (i == 4)
        line << " ";
    }
    DEMU_INFO("{}", line.str());
  }
}

void CPUSimulator::save_trace(const std::string &filename) {
  trace_->save(filename);
  DEMU_INFO("Trace saved to trace.log")
}

void CPUSimulator::check_termination() {
  instr_t ebreak_instr = EBREAK;
  if (static_cast<instr_t>(dut_->debug_instr) == ebreak_instr) {
    DEMU_INFO("[TERMINATION] EBREAK instruction at PC=0x{:08x}",
              static_cast<addr_t>(dut_->debug_pc));
    terminate_ = true;
  }
}

} // namespace demu
