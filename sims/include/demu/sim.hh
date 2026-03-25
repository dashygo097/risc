#pragma once

#include "./config.hh"
#include "./hal/hal.hh"
#include "Vrv32i_system.h"
#include "verilated.h"
#include <cstdint>
#include <memory>
#include <string>

#ifdef ENABLE_TRACE
#include "verilated_vcd_c.h"
#endif

namespace demu {
using namespace isa;

class DemuSimulator {
public:
  DemuSimulator(bool enabled_trace = false, int threads = 1, int argc = 0,
                char **argv = nullptr);
  ~DemuSimulator();

  // Program loading
  bool load_bin(const std::string &filename, addr_t offset = 0);
  bool load_elf(const std::string &filename);

  // Simulation control
  void init();
  void reset();
  void step(uint64_t cycles = 1);
  void run(uint64_t max_cycles = 0);

  // Architecture state access
  [[nodiscard]] hal::Device *device(addr_t addr) {
    return device_manager_->find_device_for_address(addr);
  }
  [[nodiscard]] addr_t pc() const noexcept {
    return static_cast<addr_t>(dut_->debug_pc);
  };
  [[nodiscard]] word_t reg(uint8_t reg) const noexcept {
    auto it = _register_values.find(reg);
    return it != _register_values.end() ? it->second : 0;
  };
  [[nodiscard]] instr_t if_instr() const noexcept {
    return static_cast<instr_t>(dut_->debug_if_instr);
  }
  [[nodiscard]] instr_t id_instr() const noexcept {
    return static_cast<instr_t>(dut_->debug_id_instr);
  }
  [[nodiscard]] instr_t ex_instr() const noexcept {
    return static_cast<instr_t>(dut_->debug_ex_instr);
  }
  [[nodiscard]] instr_t mem_instr() const noexcept {
    return static_cast<instr_t>(dut_->debug_mem_instr);
  }
  [[nodiscard]] instr_t wb_instr() const noexcept {
    return static_cast<instr_t>(dut_->debug_wb_instr);
  }

  // Simulator configuration
  void timeout(uint64_t timeout) noexcept { timeout_ = timeout; }
  void show_pipeline(bool show) noexcept { show_pipeline_ = show; }

  // Simulator statistics
  [[nodiscard]] uint64_t cycle_count() const noexcept {
    return dut_->debug_cycle_count;
  }
  [[nodiscard]] uint64_t instret_count() const {
    return dut_->debug_instret_count;
  }
  [[nodiscard]] double ipc() const noexcept {
    return dut_->debug_cycle_count > 0
               ? (double)dut_->debug_instret_count / dut_->debug_cycle_count
               : 0.0;
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

  // Debug output
  void dump_registers() const;
  void dump_memory(addr_t start, size_t size) const;

protected:
  // components
  std::unique_ptr<RiscConfig> config_;

  std::unique_ptr<VerilatedContext> context_;
  std::unique_ptr<system_t> dut_;
  std::unique_ptr<hal::DeviceManager> device_manager_;

#ifdef ENABLE_TRACE
  std::unique_ptr<VerilatedVcdC> vcd_;
#endif

  uint64_t timeout_{1000000};
  bool show_pipeline_{false};
  bool trace_enabled_{false};

  // Simulator state
  bool _terminate{false};

  uint64_t _l1_icache_accesses{0};
  uint64_t _l1_icache_misses{0};
  uint64_t _l1_dcache_accesses{0};
  uint64_t _l1_dcache_misses{0};

  std::map<uint8_t, word_t> _register_values;

  // Internal simulation methods
  void clock_tick();
  void handle_cache_profiling();

  // Overridable hooks
  virtual void register_devices() {};
  virtual void on_clock_tick() {};
  virtual void on_init() {};
  virtual void on_exit() {};
  virtual void on_reset() {};
};
} // namespace demu
