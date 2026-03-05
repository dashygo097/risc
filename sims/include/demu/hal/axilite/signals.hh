#pragma once

#include "../../isa/isa.hh"
#include <cstdint>

#define MAP_AXIL_SIGNALS(name, port_id)                                        \
  name.awaddr = &dut_->M_AXIL_##port_id##_AWADDR;                              \
  name.awprot = &dut_->M_AXIL_##port_id##_AWPROT;                              \
  name.awvalid = &dut_->M_AXIL_##port_id##_AWVALID;                            \
  name.awready = &dut_->M_AXIL_##port_id##_AWREADY;                            \
  name.wdata = &dut_->M_AXIL_##port_id##_WDATA;                                \
  name.wstrb = &dut_->M_AXIL_##port_id##_WSTRB;                                \
  name.wvalid = &dut_->M_AXIL_##port_id##_WVALID;                              \
  name.wready = &dut_->M_AXIL_##port_id##_WREADY;                              \
  name.bresp = &dut_->M_AXIL_##port_id##_BRESP;                                \
  name.bvalid = &dut_->M_AXIL_##port_id##_BVALID;                              \
  name.bready = &dut_->M_AXIL_##port_id##_BREADY;                              \
  name.araddr = &dut_->M_AXIL_##port_id##_ARADDR;                              \
  name.arprot = &dut_->M_AXIL_##port_id##_ARPROT;                              \
  name.arvalid = &dut_->M_AXIL_##port_id##_ARVALID;                            \
  name.arready = &dut_->M_AXIL_##port_id##_ARREADY;                            \
  name.rdata = &dut_->M_AXIL_##port_id##_RDATA;                                \
  name.rresp = &dut_->M_AXIL_##port_id##_RRESP;                                \
  name.rvalid = &dut_->M_AXIL_##port_id##_RVALID;                              \
  name.rready = &dut_->M_AXIL_##port_id##_RREADY;

namespace demu::hal::axi {
using namespace isa;

struct AXILiteSignals {
  uint32_t *awaddr;
  uint8_t *awprot;
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
  uint8_t *arprot;
  uint8_t *arvalid;
  uint8_t *arready;
  uint32_t *rdata;
  uint8_t *rresp;
  uint8_t *rvalid;
  uint8_t *rready;
};

} // namespace demu::hal::axi
