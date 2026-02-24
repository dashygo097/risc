#pragma once

#include "./hal/hal.hh"
#include "./logger.hh"
#include "./trace.hh"
#include "Vrv32i_cpu.h"
#include "verilated.h"
#include <cstdint>
#include <map>
#include <memory>
#include <string>

#ifdef ENABLE_TRACE
#include "verilated_vcd_c.h"
#endif

#define IMEM_SET_RESP_DATA(dut, data_ptr, num_words)                           \
  do {                                                                         \
    for (int i = 0; i < (num_words); i++) {                                    \
      *(&((dut)->imem_resp_bits_data_0) + i) = *(data_ptr + i);                \
    }                                                                          \
  } while (0);

#define IMEM_CLEAR_RESP_DATA(dut, num_words)                                   \
  do {                                                                         \
    for (int i = 0; i < (num_words); i++) {                                    \
      *(&((dut)->imem_resp_bits_data_0) + i) = 0;                              \
    }                                                                          \
  } while (0);

#define DMEM_SET_RESP_DATA(dut, data_ptr, num_words)                           \
  do {                                                                         \
    for (int i = 0; i < (num_words); i++) {                                    \
      *(&((dut)->dmem_resp_bits_data_0) + i) = *(data_ptr + i);                \
    }                                                                          \
  } while (0);

#define DMEM_GET_REQ_DATA(dut, data_ptr, num_words)                            \
  do {                                                                         \
    for (int i = 0; i < (num_words); i++) {                                    \
      *(data_ptr + i) = *(&((dut)->dmem_req_bits_data_0) + i);                 \
    }                                                                          \
  } while (0);

#define DMEM_CLEAR_RESP_DATA(dut, num_words)                                   \
  do {                                                                         \
    for (int i = 0; i < (num_words); i++) {                                    \
      *(&((dut)->dmem_resp_bits_data_0) + i) = 0;                              \
    }                                                                          \
  } while (0);

namespace demu {
using namespace isa;

class CPUSimulator {
public:
  CPUSimulator(bool enable_trace = false);
  ~CPUSimulator();

  // Program loading
  bool load_bin(const std::string &filename, addr_t base_addr = 0x0);
  bool load_elf(const std::string &filename);

  // Simulation control
  void reset();
  void step(uint64_t cycles = 1);
  void run(uint64_t max_cycles = 0);
  void run_until(addr_t pc);

  // Architecture state access
  [[nodiscard]] addr_t pc() const noexcept {
    return static_cast<addr_t>(_dut->debug_pc);
  };
  [[nodiscard]] word_t reg(uint8_t reg) const noexcept {
    auto it = _register_values.find(reg);
    return it != _register_values.end() ? it->second : 0;
  };
  word_t read_mem(addr_t addr) const;
  void write_mem(addr_t addr, word_t data);

  // Simulator statistics
  [[nodiscard]] uint64_t cycle_count() const noexcept { return _cycle_count; }
  [[nodiscard]] uint64_t instr_count() const { return _instr_count; }
  [[nodiscard]] double ipc() const noexcept {
    return _cycle_count > 0 ? (double)_instr_count / _cycle_count : 0.0;
  };
  [[nodiscard]] double l1_icache_hit_rate() const noexcept {
    return _l1_icache_accesses > 0
               ? 1.0 - (double)_l1_icache_misses / _l1_icache_accesses
               : 0.0;
  };
  [[nodiscard]] double l1_dcache_hit_rate() const noexcept {
    return _l1_dcache_accesses > 0
               ? 1.0 - (double)_l1_dcache_misses / _l1_dcache_accesses
               : 0.0;
  };

  // Simulator configuration
  void verbose(bool verbose) noexcept { _verbose = verbose; }
  void timeout(uint64_t timeout) noexcept { _timeout = timeout; }
  void show_pipeline(bool show) noexcept { _show_pipeline = show; }

  // Debug output
  void dump_registers() const;
  void dump_memory(addr_t start, size_t size) const;
  void save_trace(const std::string &filename);

protected:
  // DUT and memory
  std::unique_ptr<cpu_t> _dut;
  std::unique_ptr<hal::Memory> _imem;
  std::unique_ptr<hal::Memory> _dmem;
  std::unique_ptr<ExecutionTrace> _trace;

#ifdef ENABLE_TRACE
  std::unique_ptr<VerilatedVcdC> _vcd;
#endif

  // Simulator state
  uint8_t _imem_delay{1};
  uint8_t _dmem_delay{1};
  uint64_t _time_counter;
  uint64_t _cycle_count;
  uint64_t _instr_count;
  uint64_t _timeout;
  bool _terminate;
  bool _verbose;
  bool _show_pipeline;
  bool _trace_enabled;

  // Cache profiler
  uint64_t _l1_icache_accesses;
  uint64_t _l1_icache_misses;
  uint64_t _l1_dcache_accesses;
  uint64_t _l1_dcache_misses;

  addr_t _imem_pending_addr;
  uint64_t _imem_pending_latency;
  bool _imem_pending;

  addr_t _dmem_pending_addr;
  std::vector<word_t> _dmem_pending_data;
  uint64_t _dmem_pending_latency;
  bool _dmem_pending_op;
  bool _dmem_pending;

  // Architecture state tracking
  std::map<uint8_t, word_t> _register_values;

  // Internal simulation methods
  void clock_tick();
  void handle_cache_profiling();
  void handle_imem_interface();
  void handle_dmem_interface();
  void check_termination();

  virtual void on_init() {
    _imem_delay = 1;
    _dmem_delay = 1;
  }
  virtual void on_clock_tick() {};
  virtual void on_exit() {};
  virtual void on_reset() {};
};

} // namespace demu
