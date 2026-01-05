#pragma once

#include "Vrv32i_cpu.h"
#include "verilated.h"
#include <cstdint>
#include <demu/isa/isa.hh>
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
  void show_pipeline(bool show) { _show_pipeline = show; }

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
  bool _show_pipeline;
  bool _trace_enabled;

  demu::isa::addr_t _imem_pending_addr;
  uint64_t _imem_pending_latency;
  bool _imem_pending;

  demu::isa::addr_t _dmem_pending_addr;
  demu::isa::word_t _dmem_pending_data;
  uint64_t _dmem_pending_latency;
  bool _dmem_pending_op;
  bool _dmem_pending;

  // Architecture state tracking
  std::map<uint8_t, demu::isa::word_t> _register_values;

  // Internal simulation methods
  void clock_tick();
  void handle_imem_interface();
  void handle_dmem_interface();
  void check_termination();
};
