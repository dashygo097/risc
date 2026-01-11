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
  void save_trace(const std::string &filename);

private:
  // DUT
  std::unique_ptr<system_t> _dut;

  // AXI Bus
  std::unique_ptr<hal::AXIBusManager> _axi_bus;
  hal::AXIMemory *_dmem;
  hal::AXIMemory *_imem;

  // Execution trace
  bool _trace_enabled;
  std::unique_ptr<ExecutionTrace> _trace;
#ifdef ENABLE_TRACE
  std::unique_ptr<VerilatedVcdC> _vcd;
#endif

  // Simulator state
  uint64_t _time_counter;
  uint64_t _timeout;
  bool _terminate;
  bool _verbose;

  // Internal simulation methods
  void clock_tick();
  void handle_axi_port(uint8_t port) {
    auto *slave = _axi_bus->get_slave(port);
    if (!slave)
      return;

    auto [awaddr, awvalid, awready, wdata, wstrb, wvalid, wready, bresp, bvalid,
          bready, araddr, arvalid, arready, rdata, rresp, rvalid, rready] =
        get_axi_signals(port);

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

  hal::AXISignals get_axi_signals(uint8_t port) {
    hal::AXISignals signals;

    if (port == 0) {
      signals.awaddr = &_dut->M_AXI_0_AWADDR;
      signals.awvalid = &_dut->M_AXI_0_AWVALID;
      signals.awready = &_dut->M_AXI_0_AWREADY;
      signals.wdata = &_dut->M_AXI_0_WDATA;
      signals.wstrb = &_dut->M_AXI_0_WSTRB;
      signals.wvalid = &_dut->M_AXI_0_WVALID;
      signals.wready = &_dut->M_AXI_0_WREADY;
      signals.bresp = &_dut->M_AXI_0_BRESP;
      signals.bvalid = &_dut->M_AXI_0_BVALID;
      signals.bready = &_dut->M_AXI_0_BREADY;
      signals.araddr = &_dut->M_AXI_0_ARADDR;
      signals.arvalid = &_dut->M_AXI_0_ARVALID;
      signals.arready = &_dut->M_AXI_0_ARREADY;
      signals.rdata = &_dut->M_AXI_0_RDATA;
      signals.rresp = &_dut->M_AXI_0_RRESP;
      signals.rvalid = &_dut->M_AXI_0_RVALID;
      signals.rready = &_dut->M_AXI_0_RREADY;

    } else if (port == 1) {
      signals.awaddr = &_dut->M_AXI_1_AWADDR;
      signals.awvalid = &_dut->M_AXI_1_AWVALID;
      signals.awready = &_dut->M_AXI_1_AWREADY;
      signals.wdata = &_dut->M_AXI_1_WDATA;
      signals.wstrb = &_dut->M_AXI_1_WSTRB;
      signals.wvalid = &_dut->M_AXI_1_WVALID;
      signals.wready = &_dut->M_AXI_1_WREADY;
      signals.bresp = &_dut->M_AXI_1_BRESP;
      signals.bvalid = &_dut->M_AXI_1_BVALID;
      signals.bready = &_dut->M_AXI_1_BREADY;
      signals.araddr = &_dut->M_AXI_1_ARADDR;
      signals.arvalid = &_dut->M_AXI_1_ARVALID;
      signals.arready = &_dut->M_AXI_1_ARREADY;
      signals.rdata = &_dut->M_AXI_1_RDATA;
      signals.rresp = &_dut->M_AXI_1_RRESP;
      signals.rvalid = &_dut->M_AXI_1_RVALID;
      signals.rready = &_dut->M_AXI_1_RREADY;
    }

    return signals;
  }
};
} // namespace demu
#endif
