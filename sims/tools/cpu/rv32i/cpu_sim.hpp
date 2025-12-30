#pragma once

#include "Vrv32i_cpu.h"
#include "verilated.h"
#include <cstdint>
#include <demu/memory.hh>
#include <demu/trace.hh>
#include <map>
#include <memory>
#include <string>

#ifdef ENABLE_TRACE
#include "verilated_vcd_c.h"
#endif

class CPUSimulator {
public:
  CPUSimulator(bool enable_trace = false);
  ~CPUSimulator();

  // Program loading
  bool load_bin(const std::string &filename, demu::isa::addr_t base_addr = 0x0);
  bool load_elf(const std::string &filename);

  // Simulation control
  void reset();
  void step(int cycles = 1);
  void run(uint64_t max_cycles = 0);
  void run_until(demu::isa::addr_t pc);

  // Architecture state access
  demu::isa::addr_t get_pc() const;
  demu::isa::word_t get_reg(uint8_t reg) const;
  demu::isa::word_t read_mem(demu::isa::addr_t addr) const;
  void write_mem(demu::isa::addr_t addr, demu::isa::word_t data);

  // Simulator statistics
  uint64_t get_cycle_count() const { return _dut->debug_cycles; }
  uint64_t get_inst_count() const { return _inst_count; }
  double get_ipc() const;

  // Simulator configuration
  void verbose(bool verbose) { _verbose = verbose; }
  void timeout(uint64_t timeout) { _timeout = timeout; }
  void profiling(bool enable) { _profiling = enable; }

  // Debug output
  void dump_registers() const;
  void dump_memory(demu::isa::addr_t start, size_t size) const;
  void save_trace(const std::string &filename);

private:
  // DUT and memory
  std::unique_ptr<Vrv32i_cpu> _dut;
  std::unique_ptr<demu::Memory> _imem;
  std::unique_ptr<demu::Memory> _dmem;
  std::unique_ptr<demu::ExecutionTrace> _trace;

#ifdef ENABLE_TRACE
  std::unique_ptr<VerilatedVcdC> _vcd;
#endif

  // Simulator state
  uint64_t _time_counter;
  uint64_t _inst_count;
  uint64_t _timeout;
  bool _terminate;
  bool _verbose;
  bool _profiling;
  bool _trace_enabled;

  // Architecture state tracking
  std::map<uint8_t, demu::isa::word_t> _register_values;
  std::map<demu::isa::addr_t, uint64_t> _pc_histogram;

  // Internal simulation methods
  void clock_tick();
  void handle_imem_interface();
  void handle_dmem_interface();
  void check_termination();
};
