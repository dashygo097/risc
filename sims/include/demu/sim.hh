#pragma once

#include "./config.hh"
#include "./hal/hal.hh"
#include "verilated.h"
#include <cstdint>
#include <memory>
#include <string>

#ifdef ENABLE_TRACE
#include "verilated_vcd_c.h"
#endif

#if defined(__ISA_RV32IM__)
#include "Vrv32im_system.h"

#elif defined(__ISA_RV32I__)
#include "Vrv32i_system.h"

#endif

namespace demu {
using namespace isa;

class DemuSimulator {
public:
  explicit DemuSimulator(bool enabled_trace = false, int threads = 1,
                         int argc = 0, char **argv = nullptr);
  ~DemuSimulator();

  // Program loading
  auto load_bin(const std::string &filename, addr_t offset = 0) -> bool;
  auto load_elf(const std::string &filename) -> bool;

  // Simulation control
  void init();
  void reset();
  void step(uint64_t cycles = 1);
  void run(uint64_t max_cycles = 0);

  // Architecture state access
  [[nodiscard]] auto device(addr_t addr) -> hal::Device * {
    return device_manager_->find_device_for_address(addr);
  }
  [[nodiscard]] auto pc() const noexcept -> addr_t {
    return static_cast<addr_t>(dut_->debug_pc);
  };
  [[nodiscard]] auto reg(uint8_t reg) const noexcept -> word_t {
    auto it = _register_values.find(reg);
    return it != _register_values.end() ? it->second : 0;
  };
  [[nodiscard]] auto if_instr() const noexcept -> instr_t {
    return static_cast<instr_t>(dut_->debug_if_instr);
  }
  [[nodiscard]] auto id_instr() const noexcept -> instr_t {
    return static_cast<instr_t>(dut_->debug_id_instr);
  }
  [[nodiscard]] auto ex_instr() const noexcept -> instr_t {
    return static_cast<instr_t>(dut_->debug_ex_instr);
  }
  [[nodiscard]] auto mem_instr() const noexcept -> instr_t {
    return static_cast<instr_t>(dut_->debug_mem_instr);
  }
  [[nodiscard]] auto wb_instr() const noexcept -> instr_t {
    return static_cast<instr_t>(dut_->debug_wb_instr);
  }

  // Simulator configuration
  void timeout(uint64_t timeout) noexcept { timeout_ = timeout; }
  void show_pipeline(bool show) noexcept { show_pipeline_ = show; }

  // Simulator statistics
  [[nodiscard]] auto cycle_count() const noexcept -> uint64_t {
    return dut_->debug_cycle_count;
  }
  [[nodiscard]] auto instret_count() const -> uint64_t {
    return dut_->debug_instret_count;
  }
  [[nodiscard]] auto ipc() const noexcept -> double {
    return dut_->debug_cycle_count > 0
               ? static_cast<double>(dut_->debug_instret_count) /
                     dut_->debug_cycle_count
               : 0.0;
  };
  [[nodiscard]] auto l1_icache_hit_rate() const noexcept -> double {
    return _l1_icache_accesses > 0
               ? 1.0 - static_cast<double>(_l1_icache_misses) /
                           _l1_icache_accesses
               : 0.0;
  };
  [[nodiscard]] auto l1_dcache_hit_rate() const noexcept -> double {
    return _l1_dcache_accesses > 0
               ? 1.0 - static_cast<double>(_l1_dcache_misses) /
                           _l1_dcache_accesses
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

  std::unique_ptr<demu::hal::InterruptLine> timer_irq_;
  std::unique_ptr<demu::hal::InterruptLine> soft_irq_;

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
  void handle_interrupt();
  void handle_cache_profiling();

  // Overridable hooks
  virtual void register_devices() {};
  virtual void on_clock_tick() {};
  virtual void on_init() {};
  virtual void on_exit() {};
  virtual void on_reset() {};
};
} // namespace demu
