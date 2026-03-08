#pragma once

#ifdef ENABLE_SYSTEM
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

class SystemSimulator {
public:
  SystemSimulator(bool enabled_trace = false);
  ~SystemSimulator();

  // Program loading
  bool load_bin(const std::string &filename, addr_t offset = 0);
  bool load_elf(const std::string &filename);

  // Simulation control
  void init();
  void reset();
  void step(uint64_t cycles = 1);
  void run(uint64_t max_cycles = 0);
  void check_termination();

  // Simulator configuration
  void timeout(uint64_t timeout) noexcept { _timeout = timeout; }

  // Debug output
  void dump_memory(addr_t start, size_t size) const;

  // Exported Ports
  [[nodiscard]] system_t &dut() noexcept { return *dut_; }
  [[nodiscard]] hal::DeviceManager &deviceManager() noexcept {
    return *device_manager_;
  }
  [[nodiscard]] hal::axi::AXILiteMemory &dmem() noexcept { return *dmem_; }
  [[nodiscard]] hal::axi::AXILiteMemory &imem() noexcept { return *imem_; }

protected:
  // DUT
  std::unique_ptr<system_t> dut_;
  std::unique_ptr<RiscConfig> config_;

#ifdef ENABLE_TRACE
  std::unique_ptr<VerilatedVcdC> vcd_;
#endif

  // Devices
  std::unique_ptr<hal::DeviceManager> device_manager_;
  hal::axi::AXILiteMemory *dmem_;
  hal::axi::AXILiteMemory *imem_;

  // Simulator state
  uint64_t _time_count{0};
  uint64_t _cycle_count{0};
  uint64_t _timeout{1000000};
  bool _trace_enabled{false};
  bool _terminate{false};

  // Internal simulation methods
  void clock_tick();

  // Overridable hooks
  virtual void register_devices() {};
  virtual void on_clock_tick() {};
  virtual void on_init() {
    imem_->read_delay(1);
    imem_->write_delay(1);
    dmem_->read_delay(1);
    dmem_->write_delay(1);
  };
  virtual void on_exit() {};
  virtual void on_reset() {};
};
} // namespace demu
#endif
