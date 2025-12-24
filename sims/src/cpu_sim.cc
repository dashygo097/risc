#include "cpu_sim.hh"
#include "elf_loader.hh"
#include "instruction.hh"
#include <iomanip>
#include <iostream>

CPUSimulator::CPUSimulator(bool enable_trace)
    : _dut(new Vrv32_cpu), _imem(new Memory(256 * 1024, 0x00000000)),
      _dmem(new Memory(256 * 1024, 0x80000000)), _trace(new ExecutionTrace()),
      _time_counter(0), _cycle_count(0), _inst_count(0), _timeout(1000000),
      _verbose(false), _profiling(false), _trace_enabled(enable_trace) {
#ifdef ENABLE_TRACE
  if (_trace_enabled) {
    Verilated::traceEverOn(true);
    _vcd = std::make_unique<VerilatedVcdC>();
    _dut->trace(_vcd.get(), 99);
    _vcd->open("rv32_cpu.vcd");
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

bool CPUSimulator::load_bin(const std::string &filename, uint32_t base_addr) {
  return _imem->load_binary(filename, base_addr);
}

bool CPUSimulator::load_elf(const std::string &filename) {
  return ELFLoader::load(filename, *_imem);
}

void CPUSimulator::reset() {
  _dut->reset = 1;
  _dut->clock = 0;
  _dut->eval();

  clock_tick();

  _dut->reset = 0;
  _dut->eval();

  _cycle_count = 0;
  _inst_count = 0;

  _register_values.clear();
  _pc_histogram.clear();
  _trace->clear();
}

void CPUSimulator::clock_tick() {
  _dut->IMEM_INST = _imem->read32(_dut->IMEM_ADDR);

  _dut->clock = 1;
  _dut->eval();

  if (_dut->DMEM_READ_EN) {
    uint32_t aligned_addr = _dut->DMEM_ADDR & ~0x3;
    _dut->DMEM_READ_DATA = _dmem->read32(aligned_addr);
    if (_verbose) {
      std::cout << "  [MEM READ] addr=0x" << std::hex << _dut->DMEM_ADDR
                << " aligned=0x" << aligned_addr << " data=0x"
                << _dut->DMEM_READ_DATA << std::dec << std::endl;
    }
    _dut->eval();
  } else {
    _dut->DMEM_READ_DATA = 0;
  }

#ifdef ENABLE_TRACE
  if (_vcd) {
    _vcd->dump(_time_counter++);
  }
#endif

  if (_dut->DMEM_WRITE_EN) {
    write_mem_with_strobe(_dut->DMEM_ADDR, _dut->DMEM_WRITE_DATA,
                          _dut->DMEM_WRITE_STRB);
  }

  if (_dut->DEBUG_REG_WE) {
    _register_values[_dut->DEBUG_REG_ADDR] = _dut->DEBUG_REG_DATA;
    _inst_count++;

    if (_trace_enabled) {
      TraceEntry entry;
      entry.cycle = _cycle_count;
      entry.pc = _dut->DEBUG_PC;
      entry.inst = _dut->DEBUG_INST;
      entry.rd = _dut->DEBUG_REG_ADDR;
      entry.rd_val = _dut->DEBUG_REG_DATA;
      entry.rd_written = true;

      Instruction inst(_dut->DEBUG_INST);
      entry.disasm = inst.to_string();

      _trace->add_entry(entry);
    }

    if (_profiling) {
      _pc_histogram[_dut->DEBUG_PC]++;
    }

    if (_verbose) {
      std::cout << "Cycle " << std::dec << std::setw(6) << _cycle_count
                << " | PC=0x" << std::hex << std::setw(8) << std::setfill('0')
                << _dut->DEBUG_PC << " | Inst=0x" << std::setw(8)
                << _dut->DEBUG_INST << " | x" << std::dec
                << (int)_dut->DEBUG_REG_ADDR << "=0x" << std::hex
                << std::setw(8) << _dut->DEBUG_REG_DATA << std::dec
                << std::endl;
    }
  }

  _dut->clock = 0;
  _dut->eval();

#ifdef ENABLE_TRACE
  if (_vcd) {
    _vcd->dump(_time_counter++);
  }
#endif

  _cycle_count++;
}

void CPUSimulator::step(int cycles) {
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

void CPUSimulator::run_until(uint32_t pc) {
  while (_dut->DEBUG_PC != pc && _cycle_count < _timeout) {
    clock_tick();
  }
}

uint32_t CPUSimulator::get_pc() const { return _dut->DEBUG_PC; }

uint32_t CPUSimulator::get_reg(uint8_t reg) const {
  if (reg == 0)
    return 0;
  auto it = _register_values.find(reg);
  return it != _register_values.end() ? it->second : 0;
}

uint32_t CPUSimulator::read_mem(uint32_t addr) const {
  if (addr >= _dmem->base_addr()) {
    return _dmem->read32(addr);
  }
  return _imem->read32(addr);
}

void CPUSimulator::write_mem(uint32_t addr, uint32_t data) {
  if (addr >= _dmem->base_addr()) {
    _dmem->write32(addr, data);
  } else {
    _imem->write32(addr, data);
  }
}

double CPUSimulator::get_ipc() const {
  return _cycle_count > 0 ? (double)_inst_count / _cycle_count : 0.0;
}

void CPUSimulator::dump_registers() const {
  std::cout << "\n========================================\n";
  std::cout << "Register Dump\n";
  std::cout << "========================================\n";

  std::cout << "x00" << " = 0x" << std::hex << std::setw(8) << std::setfill('0')
            << get_reg(0) << "  ";
  for (int i = 1; i < 32; i++) {
    std::cout << "x" << std::dec << std::setw(2) << i << " = 0x" << std::hex
              << std::setw(8) << std::setfill('0') << get_reg(i);
    if (i % 4 == 3)
      std::cout << "\n";
    else
      std::cout << "  ";
  }
  std::cout << std::dec << std::endl;
}

void CPUSimulator::dump_memory(uint32_t start, uint32_t size) const {
  std::cout << "\n========================================\n";
  std::cout << "Memory Dump: 0x" << std::hex << start << " - 0x"
            << (start + size) << "\n";
  std::cout << "========================================\n";

  for (uint32_t addr = start; addr < start + size; addr += 16) {
    std::cout << std::hex << std::setw(8) << std::setfill('0') << addr << ": ";
    for (uint32_t i = 0; i < 16 && (addr + i) < (start + size); i += 4) {
      uint32_t word = read_mem(addr + i);

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

void CPUSimulator::write_mem_with_strobe(uint32_t addr, uint32_t data,
                                         uint8_t strb) {
  uint32_t aligned_addr = addr & ~0x3;

  uint32_t old_word = _dmem->read32(aligned_addr);
  uint32_t new_word = old_word;

  for (int i = 0; i < 4; i++) {
    if (strb & (1 << i)) {
      uint32_t byte_mask = 0xFF << (i * 8);
      new_word = (new_word & ~byte_mask) | (data & byte_mask);
    }
  }

  _dmem->write32(aligned_addr, new_word);

  if (_verbose) {
    std::cout << "  [MEM WRITE] addr=0x" << std::hex << addr << " aligned=0x"
              << aligned_addr << " data=0x" << data << " strb=0x" << (int)strb
              << " result=0x" << new_word << std::dec << std::endl;
  }
}

void CPUSimulator::check_termination() {
  if (_dut->DEBUG_INST == 0x00100073) {
    if (_verbose) {
      std::cout << "\n[TERMINATION] EBREAK instruction at PC=0x" << std::hex
                << _dut->DEBUG_PC << std::dec << std::endl;
    }
    _terminate = true;
  }
}
