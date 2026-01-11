#pragma once

#ifdef ENABLE_SYSTEM
#include "./hal/hal.hh"
#include "./trace.hh"
#include "Vrv32i_system.h"
#include "verilated.h"
#include <cstdint>
#include <map>
#include <memory>
#include <string>

#ifdef ENABLE_TRACE
#include "verilated_vcd_c.h"
#endif

namespace demu {
using namespace isa;
class SystemSimulator {
public:
  SystemSimulator(bool enabled_trace = false);
  ~SystemSimulator();

  // Program loading
  bool load_bin(const std::string &filename, addr_t base_addr = 0x0);
  bool load_elf(const std::string &filename);

  // Simulation control
  void reset();
  void step(uint64_t cycles = 1);
  void run(uint64_t max_cycles = 0);

  void verbose(bool verbose) noexcept { _verbose = verbose; }
  void timeout(uint64_t timeout) noexcept { _timeout = timeout; }

  void dump_memory(addr_t start, size_t size) const;
  void save_trace(const std::string &filename);

private:
  // DUT
  std::unique_ptr<system_t> _dut;

  // HAL
  std::unordered_map<std::string, std::unique_ptr<IHardware>> _hardwares;

  // Execution trace
  std::unique_ptr<ExecutionTrace> _trace;
#ifdef ENABLE_TRACE
  std::unique_ptr<VerilatedVcdC> _vcd;
#endif

  // Simulator state
  uint64_t _time_counter;
  uint64_t _timeout;
  bool _terminate;
  bool _verbose;
  bool _trace_enabled;

  // Internal simulation methods
  void clock_tick();
  void check_termination();
};
} // namespace demu
#endif
