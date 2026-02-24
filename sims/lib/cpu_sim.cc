#include "demu/cpu_sim.hh"
#include "demu/elf_loader.hh"
#include "demu/logger.hh"
#include <iomanip>
#include <iostream>

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
  on_init();
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

  cycleCount = 0;
  instrCount = 0;
  terminate_ = false;
  _register_values.clear();
  trace_->clear();

  _imem_pending = 0;
  _dmem_pending = 0;

  _dmem_pending_data.resize(4);

  on_reset();
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

        if (verbose_) {
          std::cout << "  [IMEM READ] addr=0x" << std::hex << std::setw(8)
                    << std::setfill('0') << addr << " data=0x" << std::setw(8)
                    << data << std::dec << std::endl;
        }
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

      if (verbose_) {
        if (_dmem_pending_op) {
          for (int i = 0; i < 4; i++) {
            std::cout << "  [DMEM REQ] WRITE addr=0x" << std::hex
                      << std::setw(8) << std::setfill('0')
                      << _dmem_pending_addr + (i * 4) << " data=0x"
                      << std::setw(8) << _dmem_pending_data[i] << std::endl;
          }
        } else {
          std::cout << "  [DMEM REQ] READ addr=0x" << std::hex << std::setw(8)
                    << std::setfill('0') << _dmem_pending_addr << std::endl;
        }
      }
    }
  } else {
    _dmem_pending_latency--;

    if (_dmem_pending_latency > 0) {
      dut_->dmem_resp_valid = 0;
      DMEM_CLEAR_RESP_DATA(dut_, 4)

    } else {
      if (_dmem_pending_op) {
        // Write
        for (int i = 0; i < _dmem_pending_data.size(); i++) {
          addr_t addr = _dmem_pending_addr + (i * 4);
          dmem_->write_word(addr, _dmem_pending_data[i]);
        }
        DMEM_CLEAR_RESP_DATA(dut_, 4)

        if (verbose_) {
          for (int i = 0; i < _dmem_pending_data.size(); i++) {
            addr_t addr = _dmem_pending_addr + (i * 4);
            std::cout << "  [DMEM WRITE] addr=0x" << std::hex << std::setw(8)
                      << std::setfill('0') << addr << " data=0x" << std::setw(8)
                      << _dmem_pending_data[i] << std::dec << std::endl;
          }
        }
      } else {
        // Read
        word_t data_ptr[4];
        for (int i = 0; i < 4; i++) {
          addr_t addr = _dmem_pending_addr + (i * 4);
          word_t data = dmem_->read_word(addr);
          data_ptr[i] = data;

          if (verbose_) {
            std::cout << "  [DMEM READ] addr=0x" << std::hex << std::setw(8)
                      << std::setfill('0') << _dmem_pending_addr
                      << " aligned=0x" << std::setw(8) << addr << " data=0x"
                      << std::setw(8) << data << std::dec << std::endl;
          }
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
  dut_->clock = 0;
  handle_imem_interface();
  handle_dmem_interface();
  dut_->eval();

#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->dump(timeCount++);
  }
#endif

  dut_->clock = 1;
  dut_->eval();

#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->dump(timeCount++);
  }
#endif

  cycleCount++;

  handle_cache_profiling();

  if (dut_->debug_reg_addr != 0) {
    if (dut_->debug_reg_we) {
      word_t reg_data = static_cast<word_t>(dut_->debug_reg_data);
      _register_values[dut_->debug_reg_addr] = reg_data;
    }
    if (trace_enabled_) {
      TraceEntry entry;
      entry.cycle = cycleCount;
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
    std::cout << "Cycle " << std::dec << std::setw(6) << cycleCount
              << " | IF: " << std::hex << std::setw(8) << std::setfill('0')
              << dut_->debug_if_instr << " | ID: " << std::setw(8)
              << dut_->debug_id_instr << " | EX: " << std::setw(8)
              << dut_->debug_ex_instr << " | MEM: " << std::setw(8)
              << dut_->debug_mem_instr << " | WB: " << std::setw(8)
              << dut_->debug_wb_instr << std::dec << std::endl;
  }

  if (dut_->debug_branch_taken) {
    if (verbose_) {
      std::cout << "  [BRANCH TAKEN] Source=0x" << std::hex << std::setw(8)
                << std::setfill('0') << dut_->debug_branch_source
                << " | Target=0x" << std::setw(8) << dut_->debug_branch_target
                << std::dec << std::endl;
    }
  }

  if (dut_->debug_wb_instr != BUBBLE) {
    instrCount++;
    if (verbose_) {
      Instruction inst(static_cast<instr_t>(dut_->debug_wb_instr));
      std::cout << "Cycle " << std::dec << std::setw(6) << cycleCount
                << " | PC=0x" << std::hex << std::setw(8) << std::setfill('0')
                << dut_->debug_pc << " | Inst=0x" << std::setw(8)
                << dut_->debug_wb_instr << " (" << inst.to_string() << ")"
                << std::dec << std::endl;
    }
  }

  on_clock_tick();
}

void CPUSimulator::step(uint64_t cycles) {
  for (int i = 0; i < cycles; i++) {
    clock_tick();
  }
}

void CPUSimulator::run(uint64_t max_cycles) {
  on_init();
  uint64_t target = max_cycles > 0 ? max_cycles : timeout_;
  while (cycleCount < target && !terminate_) {
    clock_tick();
    check_termination();
  }

  if (verbose_) {
    std::cout << "\nSimulation completed after " << cycleCount << " cycles\n";
  }

  on_exit();
}

void CPUSimulator::run_until(addr_t pc) {
  on_init();
  while (static_cast<addr_t>(dut_->debug_pc) != pc && cycleCount < timeout_ &&
         !terminate_) {
    clock_tick();
    check_termination();
  }

  on_exit();
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
  std::cout << "Register Dump:\n";
  std::cout << "x00 = 0x" << std::hex << std::setw(8) << std::setfill('0')
            << reg(0) << "  ";
  for (int i = 1; i < NUM_GPRS; i++) {
    std::cout << "x" << std::dec << std::setw(2) << i << " = 0x" << std::hex
              << std::setw(8) << std::setfill('0') << reg(i);
    if (i % 4 == 3)
      std::cout << "\n";
    else
      std::cout << "  ";
  }
  std::cout << std::dec << std::endl;
}

void CPUSimulator::dump_memory(addr_t start, size_t size) const {
  printf("Memory dump [0x%08x - 0x%08zx]:\n", start, start + size);
  for (addr_t addr = start; addr < start + size; addr += 16) {
    std::cout << std::hex << std::setw(8) << std::setfill('0') << addr << ": ";
    for (size_t i = 0; i < 16 && (addr + i) < (start + size); i += 4) {
      word_t word = read_mem(addr + i);
      for (int j = 0; j < 4 && (i + j) < 16; j++) {
        std::cout << std::hex << std::setw(2) << std::setfill('0')
                  << (int)((word >> (j * 8)) & 0xFF) << " ";
      }
      if (i == 4)
        std::cout << " ";
    }
    std::cout << std::endl;
  }
  std::cout << std::dec << std::endl;
}

void CPUSimulator::save_trace(const std::string &filename) {
  trace_->save(filename);
}

void CPUSimulator::check_termination() {
  instr_t ebreak_instr = EBREAK;
  if (static_cast<instr_t>(dut_->debug_instr) == ebreak_instr) {
    if (verbose_) {
      std::cout << "\n[TERMINATION] EBREAK instruction at PC=0x" << std::hex
                << dut_->debug_pc << std::dec << std::endl;
    }
    terminate_ = true;
  }
}

} // namespace demu
