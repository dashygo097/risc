#pragma once
#include "./rvsim/memory.hh"
#include "./rvsim/trace.hh"
#include "Vrv32i_cpu.h"
#include "verilated.h"
#include <cstdint>
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

  bool load_bin(const std::string &filename, uint32_t base_addr = 0x0);
  bool load_elf(const std::string &filename);

  void reset();
  void step(int cycles = 1);
  void run(uint64_t max_cycles = 0);
  void run_until(uint32_t pc);

  uint32_t get_pc() const;
  uint32_t get_reg(uint8_t reg) const;
  uint32_t read_mem(uint32_t addr) const;
  void write_mem(uint32_t addr, uint32_t data);

  uint64_t get_cycle_count() const { return _dut->debug_cycles; }
  uint64_t get_inst_count() const { return _inst_count; }
  double get_ipc() const;

  void verbose(bool verbose) { _verbose = verbose; }
  void timeout(uint64_t timeout) { _timeout = timeout; }
  void profiling(bool enable) { _profiling = enable; }

  void dump_registers() const;
  void dump_memory(uint32_t start, uint32_t size) const;
  void save_trace(const std::string &filename);

private:
  std::unique_ptr<Vrv32i_cpu> _dut;
  std::unique_ptr<Memory> _imem;
  std::unique_ptr<Memory> _dmem;
  std::unique_ptr<ExecutionTrace> _trace;
#ifdef ENABLE_TRACE
  std::unique_ptr<VerilatedVcdC> _vcd;
#endif

  uint64_t _time_counter;
  uint64_t _inst_count;
  uint64_t _timeout;
  bool _terminate;
  bool _verbose;
  bool _profiling;
  bool _trace_enabled;

  std::map<uint8_t, uint32_t> _register_values;
  std::map<uint32_t, uint64_t> _pc_histogram;

  void clock_tick();
  void handle_imem_interface();
  void handle_dmem_interface();
  void check_termination();
};
