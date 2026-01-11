#include "demu/cpu_sim.hh"
#include "demu/elf_loader.hh"
#include <iomanip>
#include <iostream>

namespace demu {
using namespace isa;

CPUSimulator::CPUSimulator(bool enable_trace)
    : _dut(new cpu_t), _imem(new hal::Memory(4 * 1024, 0x00000000)),
      _dmem(new hal::Memory(4 * 1024, 0x80000000)),
      _trace(new ExecutionTrace()), _time_counter(0), _cycle_count(0),
      _instr_count(0), _timeout(1000000), _terminate(false), _verbose(false),
      _show_pipeline(false), _trace_enabled(enable_trace) {
#ifdef ENABLE_TRACE
  if (_trace_enabled) {
    Verilated::traceEverOn(true);
    _vcd = std::make_unique<VerilatedVcdC>();
    _dut->trace(_vcd.get(), 99);
    _vcd->open(strcpy(ISA_NAME, "_cpu.vcd"));
  }
#endif
}

CPUSimulator::~CPUSimulator() {
#ifdef ENABLE_TRACE
  if (_vcd) {
    _vcd->close();
  }
#endif
}

bool CPUSimulator::load_bin(const std::string &filename, addr_t base_addr) {
  return _imem->load_binary(filename, base_addr);
}

bool CPUSimulator::load_elf(const std::string &filename) {
  return ELFLoader::load(*_imem, filename);
}

void CPUSimulator::reset() {
  _dut->reset = 1;
  _dut->clock = 0;

  _dut->imem_req_ready = 1;
  _dut->imem_resp_valid = 0;
  _dut->imem_resp_bits_data = 0;
  _dut->dmem_req_ready = 1;
  _dut->dmem_resp_valid = 0;
  _dut->dmem_resp_bits_data = 0;

  _dut->eval();

  for (int i = 0; i < 5; i++) {
    _dut->clock = 1;
    _dut->eval();
    _dut->clock = 0;
    _dut->eval();
  }

  _dut->reset = 0;
  _dut->eval();

  _cycle_count = 0;
  _instr_count = 0;
  _terminate = false;
  _register_values.clear();
  _trace->clear();

  _imem_pending = 0;
  _dmem_pending = 0;
}

void CPUSimulator::handle_imem_interface() {
  if (!_imem_pending) {
    _dut->imem_resp_valid = 0;
    _dut->imem_resp_bits_data = 0;

    if (_dut->imem_req_valid && _dut->imem_req_ready) {
      _imem_pending_addr = static_cast<addr_t>(_dut->imem_req_bits_addr);
      _imem_pending_latency = IMEM_LATENCY;
      _imem_pending = true;
    }
  } else {
    _imem_pending_latency--;

    if (_imem_pending_latency > 0) {
      _dut->imem_resp_valid = 0;
      _dut->imem_resp_bits_data = 0;
    } else {
      word_t instr = _imem->read_word(_imem_pending_addr);
      _dut->imem_resp_bits_data = instr;
      _dut->imem_resp_valid = 1;

      if (_dut->imem_resp_ready) {
        _imem_pending = false;
      }
    }
  }
}

void CPUSimulator::handle_dmem_interface() {
  if (!_dmem_pending) {
    _dut->dmem_resp_valid = 0;
    _dut->dmem_resp_bits_data = 0;

    if (_dut->dmem_req_valid && _dut->dmem_req_ready) {
      _dmem_pending_addr = static_cast<addr_t>(_dut->dmem_req_bits_addr);
      _dmem_pending_op = _dut->dmem_req_bits_op;
      _dmem_pending_data = static_cast<word_t>(_dut->dmem_req_bits_data);
      _dmem_pending_latency = DMEM_LATENCY;
      _dmem_pending = true;

      if (_verbose) {
        if (_dmem_pending_op) {
          std::cout << "  [DMEM REQ] WRITE addr=0x" << std::hex << std::setw(8)
                    << std::setfill('0') << _dmem_pending_addr << " data=0x"
                    << std::setw(8) << _dmem_pending_data << std::endl;
        } else {
          std::cout << "  [DMEM REQ] READ addr=0x" << std::hex << std::setw(8)
                    << std::setfill('0') << _dmem_pending_addr << std::endl;
        }
      }
    }
  } else {
    _dmem_pending_latency--;

    if (_dmem_pending_latency > 0) {
      _dut->dmem_resp_valid = 0;
      _dut->dmem_resp_bits_data = 0;

    } else {
      if (_dmem_pending_op) {
        // Write
        _dmem->write_word(_dmem_pending_addr, _dmem_pending_data);
        _dut->dmem_resp_bits_data = 0;

        if (_verbose) {
          std::cout << "  [DMEM WRITE] addr=0x" << std::hex << std::setw(8)
                    << std::setfill('0') << _dmem_pending_addr << " data=0x"
                    << std::setw(8) << _dmem_pending_data << std::dec
                    << std::endl;
        }
      } else {
        // Read
        addr_t aligned_addr = _dmem_pending_addr & ~0x3;
        word_t data = _dmem->read_word(aligned_addr);
        _dut->dmem_resp_bits_data = data;

        if (_verbose) {
          std::cout << "  [DMEM READ] addr=0x" << std::hex << std::setw(8)
                    << std::setfill('0') << _dmem_pending_addr << " aligned=0x"
                    << std::setw(8) << aligned_addr << " data=0x"
                    << std::setw(8) << data << std::dec << std::endl;
        }
      }

      _dut->dmem_resp_valid = 1;

      if (_dut->dmem_resp_ready) {
        _dmem_pending = false;
      }
    }
  }
}

void CPUSimulator::clock_tick() {
  _dut->clock = 0;
  handle_imem_interface();
  handle_dmem_interface();
  _dut->eval();

#ifdef ENABLE_TRACE
  if (_vcd) {
    _vcd->dump(_time_counter++);
  }
#endif

  _dut->clock = 1;
  _dut->eval();

#ifdef ENABLE_TRACE
  if (_vcd) {
    _vcd->dump(_time_counter++);
  }
#endif

  _cycle_count++;

  if (_dut->debug_reg_addr != 0) {
    if (_dut->debug_reg_we) {
      word_t reg_data = static_cast<word_t>(_dut->debug_reg_data);
      _register_values[_dut->debug_reg_addr] = reg_data;
    }
    if (_trace_enabled) {
      TraceEntry entry;
      entry.cycle = _cycle_count;
      entry.pc = static_cast<addr_t>(_dut->debug_pc);
      entry.inst = static_cast<instr_t>(_dut->debug_instr);
      entry.rd = _dut->debug_reg_addr;
      entry.rd_val = static_cast<word_t>(_dut->debug_reg_data);
      entry.regwrite = _dut->debug_reg_we;
      Instruction inst(entry.inst);
      entry.disasm = inst.to_string();
      _trace->add_entry(entry);
    }
  }

  if (_show_pipeline) {
    std::cout << "Cycle " << std::dec << std::setw(6) << _cycle_count
              << " | IF: " << std::hex << std::setw(8) << std::setfill('0')
              << _dut->debug_if_instr << " | ID: " << std::setw(8)
              << _dut->debug_id_instr << " | EX: " << std::setw(8)
              << _dut->debug_ex_instr << " | MEM: " << std::setw(8)
              << _dut->debug_mem_instr << " | WB: " << std::setw(8)
              << _dut->debug_wb_instr << std::dec << std::endl;
  }

  if (_dut->debug_branch_taken) {
    if (_verbose) {
      std::cout << "  [BRANCH TAKEN] Source=0x" << std::hex << std::setw(8)
                << std::setfill('0') << _dut->debug_branch_source
                << " | Target=0x" << std::setw(8) << _dut->debug_branch_target
                << std::dec << std::endl;
    }
  }

  if (_dut->debug_wb_instr != BUBBLE) {
    _instr_count++;
    if (_verbose) {
      Instruction inst(static_cast<instr_t>(_dut->debug_wb_instr));
      std::cout << "Cycle " << std::dec << std::setw(6) << _cycle_count
                << " | PC=0x" << std::hex << std::setw(8) << std::setfill('0')
                << _dut->debug_pc << " | Inst=0x" << std::setw(8)
                << _dut->debug_wb_instr << " (" << inst.to_string() << ")"
                << std::dec << std::endl;
    }
  }
}

void CPUSimulator::step(uint64_t cycles) {
  for (int i = 0; i < cycles; i++) {
    clock_tick();
  }
}

void CPUSimulator::run(uint64_t max_cycles) {
  uint64_t target = max_cycles > 0 ? max_cycles : _timeout;
  while (_cycle_count < target && !_terminate) {
    clock_tick();
    check_termination();
  }

  if (_verbose) {
    std::cout << "\nSimulation completed after " << _cycle_count << " cycles\n";
  }
}

void CPUSimulator::run_until(addr_t pc) {
  while (static_cast<addr_t>(_dut->debug_pc) != pc && _cycle_count < _timeout &&
         !_terminate) {
    clock_tick();
    check_termination();
  }
}

word_t CPUSimulator::read_mem(addr_t addr) const {
  if (addr >= _dmem->base_addr()) {
    return _dmem->read_word(addr);
  }
  return _imem->read_word(addr);
}

void CPUSimulator::write_mem(addr_t addr, word_t data) {
  if (addr >= _dmem->base_addr()) {
    _dmem->write_word(addr, data);
  } else {
    _imem->write_word(addr, data);
  }
}

void CPUSimulator::dump_registers() const {
  std::cout << "\n========================================\n";
  std::cout << "Register Dump\n";
  std::cout << "========================================\n";
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
  std::cout << "\n========================================\n";
  std::cout << "Memory Dump: 0x" << std::hex << start << " - 0x"
            << (start + size) << "\n";
  std::cout << "========================================\n";
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
  _trace->save(filename);
}

void CPUSimulator::check_termination() {
  instr_t ebreak_instr = EBREAK;
  if (static_cast<instr_t>(_dut->debug_instr) == ebreak_instr) {
    if (_verbose) {
      std::cout << "\n[TERMINATION] EBREAK instruction at PC=0x" << std::hex
                << _dut->debug_pc << std::dec << std::endl;
    }
    _terminate = true;
  }
}

} // namespace demu
