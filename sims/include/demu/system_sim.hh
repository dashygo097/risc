#pragma once

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

  // Simulator configuration
  void verbose(bool verbose) noexcept { _verbose = verbose; }
  void timeout(uint64_t timeout) noexcept { _timeout = timeout; }

  // Debug output
  void dump_memory(addr_t start, size_t size) const;

  // Exported Ports
  [[nodiscard]] system_t &dut() noexcept { return *_dut; }
  [[nodiscard]] hal::axi::AXIFullBusManager &axiBus() noexcept {
    return *_axi_bus;
  }
  [[nodiscard]] hal::axi::AXIFullMemory &dmem() noexcept { return *_dmem; }
  [[nodiscard]] hal::axi::AXIFullMemory &imem() noexcept { return *_imem; }

protected:
  // DUT
  std::unique_ptr<system_t> _dut;

#ifdef ENABLE_TRACE
  std::unique_ptr<VerilatedVcdC> _vcd;
#endif

  // Devices
  std::unique_ptr<hal::axi::AXIFullBusManager> _axi_bus;
  hal::axi::AXIFullMemory *_dmem;
  hal::axi::AXIFullMemory *_imem;

  // Simulator state
  uint64_t _time_counter;
  uint64_t _timeout;
  bool _trace_enabled;
  bool _terminate;
  bool _verbose;

  // Internal simulation methods
  void clock_tick();

  // Overridable hooks
  virtual void register_devices() {};
  virtual void check_termination() {};
  virtual void on_clock_tick() {};
  virtual void on_exit() {};
  virtual void on_reset() {};

  void handle_port(uint8_t port) {
    auto *slave = _axi_bus->get_slave(port);
    if (!slave)
      return;

    auto [awaddr, awprot, awid, awlen, awsize, awburst, awlock, awcache,
          awvalid, awready, awqos, awregion, wdata, wstrb, wlast, wvalid,
          wready, wid, bid, bresp, bvalid, bready, araddr, arprot, arid, arlen,
          arsize, arburst, arlock, arcache, arvalid, arready, arqos, arregion,
          rid, rdata, rresp, rlast, rvalid, rready] = from_port(port);

    if (*awvalid && slave->aw_ready()) {
      slave->aw_valid(*awaddr, *awid, *awlen, *awsize, *awburst, *awlock,
                      *awcache, *awprot, *awqos, *awregion);
    }
    *awready = slave->aw_ready();

    if (*wvalid && slave->w_ready()) {
      slave->w_valid(*wdata, *wstrb & 0xF, *wlast, *wid);
    }
    *wready = slave->w_ready();

    *bvalid = slave->b_valid();
    *bresp = slave->b_resp();
    slave->b_ready(*bready);

    if (*arvalid && slave->ar_ready()) {
      slave->ar_valid(*araddr, *arid, *arlen, *arsize, *arburst, *arlock,
                      *arcache, *arprot, *arqos, *arregion);
    }
    *arready = slave->ar_ready();

    *rid = slave->r_id();
    *rdata = slave->r_data();
    *rresp = slave->r_resp();
    *rlast = slave->r_last();
    *rvalid = slave->r_valid();
    slave->r_ready(*rready);
  }

  virtual hal::axi::AXIFullSignals from_port(uint8_t port) {
    hal::axi::AXIFullSignals signals;

    return signals;
  }
};
} // namespace demu
#endif
