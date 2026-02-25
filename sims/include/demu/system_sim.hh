#pragma once

#include "demu/hal/axifull/signals.hh"
#ifdef ENABLE_SYSTEM
#include "./hal/hal.hh"
#include "./trace.hh"
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
  void reset();
  void step(uint64_t cycles = 1);
  void run(uint64_t max_cycles = 0);
  void check_termination();

  // Simulator configuration
  void verbose(bool verbose) noexcept { _verbose = verbose; }
  void timeout(uint64_t timeout) noexcept { _timeout = timeout; }

  // Debug output
  void dump_memory(addr_t start, size_t size) const;

  // Exported Ports
  [[nodiscard]] system_t &dut() noexcept { return *_dut; }
  [[nodiscard]] hal::DeviceManager &deviceManager() noexcept {
    return *_device_manager;
  }
  [[nodiscard]] hal::axi::AXILiteMemory &dmem() noexcept { return *_dmem; }
  [[nodiscard]] hal::axi::AXILiteMemory &imem() noexcept { return *_imem; }

protected:
  // DUT
  std::unique_ptr<system_t> _dut;

#ifdef ENABLE_TRACE
  std::unique_ptr<VerilatedVcdC> _vcd;
#endif

  // Devices
  std::unique_ptr<hal::DeviceManager> _device_manager;
  hal::axi::AXILiteMemory *_dmem;
  hal::axi::AXILiteMemory *_imem;

  // Simulator state
  uint64_t _time_counter{0};
  uint64_t _cycle_count{0};
  uint64_t _timeout{1000000};
  bool _trace_enabled{false};
  bool _terminate{false};
  bool _verbose{false};

  // Internal simulation methods
  void clock_tick();

  // Overridable hooks
  virtual void register_devices() {};
  virtual void on_clock_tick() {};
  virtual void on_init() {
    _imem->read_delay(1);
    _imem->write_delay(1);
    _dmem->read_delay(1);
    _dmem->write_delay(1);
  };
  virtual void on_exit() {};
  virtual void on_reset() {};

  virtual void handle_port(uint8_t port) {
    auto *slave = _device_manager->get_slave<hal::axi::AXILiteSlave>(port);
    if (!slave)
      return;

    auto [awaddr, awprot, awvalid, awready, wdata, wstrb, wvalid, wready, bresp,
          bvalid, bready, araddr, arprot, arvalid, arready, rdata, rresp,
          rvalid, rready] = from_port(port);

    if (*awvalid && slave->aw_ready()) {
      slave->aw_valid(*awaddr);
    }
    *awready = slave->aw_ready();

    if (*wvalid && slave->w_ready()) {
      slave->w_valid(*wdata, *wstrb & 0xF);
    }
    *wready = slave->w_ready();

    *bvalid = slave->b_valid();
    *bresp = slave->b_resp();
    slave->b_ready(*bready);

    if (*arvalid && slave->ar_ready()) {
      slave->ar_valid(*araddr);
    }
    *arready = slave->ar_ready();

    *rvalid = slave->r_valid();
    *rdata = slave->r_data();
    *rresp = slave->r_resp();
    slave->r_ready(*rready);
  }

  virtual hal::axi::AXILiteSignals from_port(uint8_t port) {
    hal::axi::AXILiteSignals signals;
    return signals;
  }
};
} // namespace demu
#endif
