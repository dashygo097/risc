#pragma once

#include "../../isa/isa.hh"
#include <cstdint>

#define MAP_AXIL_SIGNALS(name, port_id)                                        \
  name.awaddr = &_dut->M_AXIL_##port_id##_AWADDR;                              \
  name.awvalid = &_dut->M_AXIL_##port_id##_AWVALID;                            \
  name.awready = &_dut->M_AXIL_##port_id##_AWREADY;                            \
  name.wdata = &_dut->M_AXIL_##port_id##_WDATA;                                \
  name.wstrb = &_dut->M_AXIL_##port_id##_WSTRB;                                \
  name.wvalid = &_dut->M_AXIL_##port_id##_WVALID;                              \
  name.wready = &_dut->M_AXIL_##port_id##_WREADY;                              \
  name.bresp = &_dut->M_AXIL_##port_id##_BRESP;                                \
  name.bvalid = &_dut->M_AXIL_##port_id##_BVALID;                              \
  name.bready = &_dut->M_AXIL_##port_id##_BREADY;                              \
  name.araddr = &_dut->M_AXIL_##port_id##_ARADDR;                              \
  name.arvalid = &_dut->M_AXIL_##port_id##_ARVALID;                            \
  name.arready = &_dut->M_AXIL_##port_id##_ARREADY;                            \
  name.rdata = &_dut->M_AXIL_##port_id##_RDATA;                                \
  name.rresp = &_dut->M_AXIL_##port_id##_RRESP;                                \
  name.rvalid = &_dut->M_AXIL_##port_id##_RVALID;                              \
  name.rready = &_dut->M_AXIL_##port_id##_RREADY;

namespace demu::hal::axi {
using namespace isa;

struct AXIReadTransaction {
  addr_t addr;
  bool active;
  word_t data;
  bool valid;
};

struct AXIWriteTransaction {
  addr_t addr;
  word_t data;
  byte_t strb;
  bool addr_valid;
  bool data_valid;
  bool resp_ready;
};

struct AXISignals {
  uint32_t *awaddr;
  uint8_t *awvalid;
  uint8_t *awready;
  uint32_t *wdata;
  uint8_t *wstrb;
  uint8_t *wvalid;
  uint8_t *wready;
  uint8_t *bresp;
  uint8_t *bvalid;
  uint8_t *bready;
  uint32_t *araddr;
  uint8_t *arvalid;
  uint8_t *arready;
  uint32_t *rdata;
  uint8_t *rresp;
  uint8_t *rvalid;
  uint8_t *rready;
};

} // namespace demu::hal::axi
