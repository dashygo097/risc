#pragma once

#include "../../../isa/isa.hh"
#include <cstdint>

#define MAP_AXIF_SIGNALS(dut, name, port_id)                                   \
  name.awid = &dut->M_AXIF_##port_id##_AWID;                                   \
  name.awaddr = &dut->M_AXIF_##port_id##_AWADDR;                               \
  name.awlen = &dut->M_AXIF_##port_id##_AWLEN;                                 \
  name.awsize = &dut->M_AXIF_##port_id##_AWSIZE;                               \
  name.awburst = &dut->M_AXIF_##port_id##_AWBURST;                             \
  name.awvalid = &dut->M_AXIF_##port_id##_AWVALID;                             \
  name.awready = &dut->M_AXIF_##port_id##_AWREADY;                             \
  name.wdata = &dut->M_AXIF_##port_id##_WDATA;                                 \
  name.wstrb = &dut->M_AXIF_##port_id##_WSTRB;                                 \
  name.wlast = &dut->M_AXIF_##port_id##_WLAST;                                 \
  name.wvalid = &dut->M_AXIF_##port_id##_WVALID;                               \
  name.wready = &dut->M_AXIF_##port_id##_WREADY;                               \
  name.bid = &dut->M_AXIF_##port_id##_BID;                                     \
  name.bresp = &dut->M_AXIF_##port_id##_BRESP;                                 \
  name.bvalid = &dut->M_AXIF_##port_id##_BVALID;                               \
  name.bready = &dut->M_AXIF_##port_id##_BREADY;                               \
  name.arid = &dut->M_AXIF_##port_id##_ARID;                                   \
  name.araddr = &dut->M_AXIF_##port_id##_ARADDR;                               \
  name.arlen = &dut->M_AXIF_##port_id##_ARLEN;                                 \
  name.arsize = &dut->M_AXIF_##port_id##_ARSIZE;                               \
  name.arburst = &dut->M_AXIF_##port_id##_ARBURST;                             \
  name.arvalid = &dut->M_AXIF_##port_id##_ARVALID;                             \
  name.arready = &dut->M_AXIF_##port_id##_ARREADY;                             \
  name.rid = &dut->M_AXIF_##port_id##_RID;                                     \
  name.rdata = &dut->M_AXIF_##port_id##_RDATA;                                 \
  name.rresp = &dut->M_AXIF_##port_id##_RRESP;                                 \
  name.rlast = &dut->M_AXIF_##port_id##_RLAST;                                 \
  name.rvalid = &dut->M_AXIF_##port_id##_RVALID;                               \
  name.rready = &dut->M_AXIF_##port_id##_RREADY;

namespace demu::hal::axi {
using namespace isa;

struct AXIFullSignals {
  // AW
  uint8_t *awid;
  addr_t *awaddr;
  uint8_t *awlen;
  uint8_t *awsize;
  uint8_t *awburst;
  uint8_t *awvalid;
  uint8_t *awready;

  // W
  word_t *wdata;
  uint8_t *wstrb;
  uint8_t *wlast;
  uint8_t *wvalid;
  uint8_t *wready;

  // B
  uint8_t *bid;
  uint8_t *bresp;
  uint8_t *bvalid;
  uint8_t *bready;

  // AR
  uint8_t *arid;
  addr_t *araddr;
  uint8_t *arlen;
  uint8_t *arsize;
  uint8_t *arburst;
  uint8_t *arvalid;
  uint8_t *arready;

  // R
  uint8_t *rid;
  word_t *rdata;
  uint8_t *rresp;
  uint8_t *rlast;
  uint8_t *rvalid;
  uint8_t *rready;
};

} // namespace demu::hal::axi
