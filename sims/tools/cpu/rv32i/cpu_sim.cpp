#include "cpu_sim.hpp"
#include "instruction.hpp"
#include "rvsim/elf_loader.hh"
#include <iomanip>
#include <iostream>

CPUSimulator::CPUSimulator(bool enable_trace)
    : _dut(new Vrv32i_cpu), _imem(new Memory(256 * 1024, 0x00000000)),
      _dmem(new Memory(256 * 1024, 0x80000000)), _trace(new ExecutionTrace()),
      _time_counter(0), _inst_count(0), _timeout(1000000), _terminate(false),
      _verbose(false), _profiling(false), _trace_enabled(enable_trace) {
#ifdef ENABLE_TRACE
  if (_trace_enabled) {
    Verilated::traceEverOn(true);
    _vcd = std::make_unique<VerilatedVcdC>();
    _dut->trace(_vcd.get(), 99);
    _vcd->open("rv32i_cpu.vcd");
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

  _inst_count = 0;
  _terminate = false;
  _register_values.clear();
  _pc_histogram.clear();
  _trace->clear();
}

void CPUSimulator::handle_imem_interface() {
  _dut->imem_resp_valid = 0;

  if (_dut->imem_req_valid && _dut->imem_req_ready) {
    uint32_t addr = _dut->imem_req_bits_addr;
    uint32_t data = _imem->read32(addr);
    _dut->imem_resp_bits_data = data;
    _dut->imem_resp_valid = 1;
  }
}

void CPUSimulator::handle_dmem_interface() {
  _dut->dmem_resp_valid = 0;
  if (_dut->dmem_req_valid && _dut->dmem_req_ready) {
    uint32_t addr = _dut->dmem_req_bits_addr;

    if (_dut->dmem_req_bits_op) {
      uint32_t data = _dut->dmem_req_bits_data;
      _dmem->write32(addr, data);
      _dut->dmem_resp_bits_data = 0;

      if (_verbose) {
        std::cout << "  [MEM WRITE] addr=0x" << std::hex << addr << " data=0x"
                  << data << std::dec << std::endl;
      }
    } else {
      uint32_t aligned_addr = addr & ~0x3;
      uint32_t data = _dmem->read32(aligned_addr);
      _dut->dmem_resp_bits_data = data;

      if (_verbose) {
        std::cout << "  [MEM READ] addr=0x" << std::hex << addr << " aligned=0x"
                  << aligned_addr << " data=0x" << data << std::dec
                  << std::endl;
      }
    }

    _dut->dmem_resp_valid = 1;
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

  if (_dut->debug_reg_we && _dut->debug_reg_addr != 0) {
    _register_values[_dut->debug_reg_addr] = _dut->debug_reg_data;
    _inst_count++;

    if (_trace_enabled) {
      TraceEntry entry;
      entry.cycle = _dut->debug_cycles;
      entry.pc = _dut->debug_pc;
      entry.inst = _dut->debug_instr;
      entry.rd = _dut->debug_reg_addr;
      entry.rd_val = _dut->debug_reg_data;
      entry.regwrite = _dut->debug_reg_we;
      Instruction inst(_dut->debug_instr);
      entry.disasm = inst.to_string();
      _trace->add_entry(entry);
    }

    if (_profiling) {
      _pc_histogram[_dut->debug_pc]++;
    }

    if (_verbose) {
      std::cout << "Cycle " << std::dec << std::setw(6) << _dut->debug_cycles
                << " | PC=0x" << std::hex << std::setw(8) << std::setfill('0')
                << _dut->debug_pc << " | Inst=0x" << std::setw(8)
                << _dut->debug_instr << " | x" << std::dec
                << (int)_dut->debug_reg_addr << "=0x" << std::hex
                << std::setw(8) << _dut->debug_reg_data << std::dec
                << std::endl;
    }
  }
}

void CPUSimulator::step(int cycles) {
  for (int i = 0; i < cycles; i++) {
    clock_tick();
  }
}

void CPUSimulator::run(uint64_t max_cycles) {
  uint64_t target = max_cycles > 0 ? max_cycles : _timeout;
  while (_dut->debug_cycles < target && !_terminate) {
    clock_tick();
    check_termination();
  }

  if (_verbose) {
    std::cout << "\nSimulation completed after " << _dut->debug_cycles
              << " cycles\n";
  }
}

void CPUSimulator::run_until(uint32_t pc) {
  while (_dut->debug_pc != pc && _dut->debug_cycles < _timeout && !_terminate) {
    clock_tick();
    check_termination();
  }
}

uint32_t CPUSimulator::get_pc() const { return _dut->debug_pc; }

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
  return _dut->debug_cycles > 0 ? (double)_inst_count / _dut->debug_cycles
                                : 0.0;
}

void CPUSimulator::dump_registers() const {
  std::cout << "\n========================================\n";
  std::cout << "Register Dump\n";
  std::cout << "========================================\n";
  std::cout << "x00 = 0x" << std::hex << std::setw(8) << std::setfill('0')
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

void CPUSimulator::check_termination() {
  if (_dut->debug_instr == 0x00100073) {
    if (_verbose) {
      std::cout << "\n[TERMINATION] EBREAK instruction at PC=0x" << std::hex
                << _dut->debug_pc << std::dec << std::endl;
    }
    _terminate = true;
  }
}
