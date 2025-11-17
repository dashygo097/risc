#include "cpu_sim.h"
#include "elf_loader.h"
#include "hex_loader.h"
#include "instruction.h"
#include "memory.h"
#include "trace.h"
#include <fstream>
#include <iomanip>
#include <iostream>

CPUSimulator::CPUSimulator(bool enable_trace)
    : dut_(new Vrv32_cpu),
      imem_(new Memory(256 * 1024, 0x00000000)) // 256KB instruction memory
      ,
      dmem_(new Memory(256 * 1024, 0x80000000)) // 256KB data memory
      ,
      trace_(new ExecutionTrace()), time_counter_(0), cycle_count_(0),
      inst_count_(0), timeout_(1000000) // 1M cycles default
      ,
      verbose_(false), profiling_(false), trace_enabled_(enable_trace) {
#ifdef ENABLE_TRACE
  if (trace_enabled_) {
    Verilated::traceEverOn(true);
    vcd_ = std::make_unique<VerilatedVcdC>();
    dut_->trace(vcd_.get(), 99);
    vcd_->open("rv32_cpu.vcd");
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

bool CPUSimulator::load_hex(const std::string &filename) {
  return imem_->load_hex(filename);
}

bool CPUSimulator::load_bin(const std::string &filename, uint32_t base_addr) {
  return imem_->load_binary(filename, base_addr);
}

bool CPUSimulator::load_elf(const std::string &filename) {
  return ELFLoader::load(filename, *imem_);
}

void CPUSimulator::reset() {
  dut_->reset = 1;
  clock_tick();
  clock_tick();
  dut_->reset = 0;
  clock_tick();

  cycle_count_ = 0;
  inst_count_ = 0;
  register_values_.clear();
  pc_histogram_.clear();
}

void CPUSimulator::clock_tick() {
  // Fetch instruction
  dut_->imem_inst = imem_->read32(dut_->imem_addr);

  // Rising edge
  dut_->clock = 1;
  dut_->eval();

#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->dump(time_counter_++);
  }
#endif

  // Capture state on rising edge
  if (dut_->debug_reg_write) {
    register_values_[dut_->debug_reg_addr] = dut_->debug_reg_data;
    inst_count_++;

    if (trace_enabled_) {
      ExecutionTrace::TraceEntry entry;
      entry.cycle = cycle_count_;
      entry.pc = dut_->debug_pc;
      entry.inst = dut_->debug_inst;
      entry.rd = dut_->debug_reg_addr;
      entry.rd_val = dut_->debug_reg_data;
      entry.rd_written = true;

      Instruction inst(dut_->debug_inst);
      entry.disasm = inst.to_string();

      trace_->add_entry(entry);
    }

    if (profiling_) {
      pc_histogram_[dut_->debug_pc]++;
    }

    if (verbose_) {
      std::cout << "Cycle " << std::dec << std::setw(6) << cycle_count_
                << " | PC=0x" << std::hex << std::setw(8) << std::setfill('0')
                << dut_->debug_pc << " | Inst=0x" << std::setw(8)
                << dut_->debug_inst << " | x" << std::dec
                << (int)dut_->debug_reg_addr << "=0x" << std::hex
                << std::setw(8) << dut_->debug_reg_data << std::dec
                << std::endl;
    }
  }

  // Falling edge
  dut_->clock = 0;
  dut_->eval();

#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->dump(time_counter_++);
  }
#endif

  cycle_count_++;
}

void CPUSimulator::step(int cycles) {
  for (int i = 0; i < cycles; i++) {
    clock_tick();
  }
}

void CPUSimulator::run(uint64_t max_cycles) {
  uint64_t target = max_cycles > 0 ? max_cycles : timeout_;

  while (cycle_count_ < target) {
    clock_tick();
    check_termination();
  }

  if (verbose_) {
    std::cout << "\nSimulation completed after " << cycle_count_ << " cycles\n";
  }
}

void CPUSimulator::run_until(uint32_t pc) {
  while (dut_->debug_pc != pc && cycle_count_ < timeout_) {
    clock_tick();
  }
}

uint32_t CPUSimulator::get_pc() const { return dut_->debug_pc; }

uint32_t CPUSimulator::get_reg(uint8_t reg) const {
  if (reg == 0)
    return 0;
  auto it = register_values_.find(reg);
  return it != register_values_.end() ? it->second : 0;
}

uint32_t CPUSimulator::read_mem(uint32_t addr) const {
  if (addr >= dmem_->base_addr()) {
    return dmem_->read32(addr);
  }
  return imem_->read32(addr);
}

void CPUSimulator::write_mem(uint32_t addr, uint32_t data) {
  if (addr >= dmem_->base_addr()) {
    dmem_->write32(addr, data);
  } else {
    imem_->write32(addr, data);
  }
}

double CPUSimulator::get_ipc() const {
  return cycle_count_ > 0 ? (double)inst_count_ / cycle_count_ : 0.0;
}

void CPUSimulator::dump_registers() const {
  std::cout << "\n========================================\n";
  std::cout << "Register Dump\n";
  std::cout << "========================================\n";

  for (int i = 0; i < 32; i++) {
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
    for (uint32_t i = 0; i < 16 && (addr + i) < (start + size); i++) {
      if (i == 8)
        std::cout << " ";
      std::cout << std::hex << std::setw(2) << std::setfill('0')
                << (int)((read_mem(addr + i) >> ((i % 4) * 8)) & 0xFF) << " ";
    }
    std::cout << std::endl;
  }
  std::cout << std::dec << std::endl;
}

void CPUSimulator::save_trace(const std::string &filename) {
  trace_->save(filename);
}

void CPUSimulator::check_termination() {
  // Check for infinite loop or halt condition
  // You can customize this based on your needs
}
