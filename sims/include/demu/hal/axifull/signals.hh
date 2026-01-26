#pragma once

#include "../../isa/isa.hh"
#include <cstdint>

#define MAP_AXIF_SIGNALS(name, port_id)                                        \
  name.awaddr = &_dut->M_AXIF_##port_id##_AWADDR;                              \
  name.awprot = &_dut->M_AXIF_##port_id##_AWPROT;                              \
  name.awid = &_dut->M_AXIF_##port_id##_AWID;                                  \
  name.awlen = &_dut->M_AXIF_##port_id##_AWLEN;                                \
  name.awsize = &_dut->M_AXIF_##port_id##_AWSIZE;                              \
  name.awburst = &_dut->M_AXIF_##port_id##_AWBURST;                            \
  name.awlock = &_dut->M_AXIF_##port_id##_AWLOCK;                              \
  name.awcache = &_dut->M_AXIF_##port_id##_AWCACHE;                            \
  name.awvalid = &_dut->M_AXIF_##port_id##_AWVALID;                            \
  name.awready = &_dut->M_AXIF_##port_id##_AWREADY;                            \
  name.awqos = &_dut->M_AXIF_##port_id##_AWQOS;                                \
  name.awregion = &_dut->M_AXIF_##port_id##_AWREGION;                          \
  name.wdata = &_dut->M_AXIF_##port_id##_WDATA;                                \
  name.wstrb = &_dut->M_AXIF_##port_id##_WSTRB;                                \
  name.wlast = &_dut->M_AXIF_##port_id##_WLAST;                                \
  name.wvalid = &_dut->M_AXIF_##port_id##_WVALID;                              \
  name.wready = &_dut->M_AXIF_##port_id##_WREADY;                              \
  name.wid = &_dut->M_AXIF_##port_id##_WID;                                    \
  name.bid = &_dut->M_AXIF_##port_id##_BID;                                    \
  name.bresp = &_dut->M_AXIF_##port_id##_BRESP;                                \
  name.bvalid = &_dut->M_AXIF_##port_id##_BVALID;                              \
  name.bready = &_dut->M_AXIF_##port_id##_BREADY;                              \
  name.araddr = &_dut->M_AXIF_##port_id##_ARADDR;                              \
  name.arprot = &_dut->M_AXIF_##port_id##_ARPROT;                              \
  name.arid = &_dut->M_AXIF_##port_id##_ARID;                                  \
  name.arlen = &_dut->M_AXIF_##port_id##_ARLEN;                                \
  name.arsize = &_dut->M_AXIF_##port_id##_ARSIZE;                              \
  name.arburst = &_dut->M_AXIF_##port_id##_ARBURST;                            \
  name.arlock = &_dut->M_AXIF_##port_id##_ARLOCK;                              \
  name.arcache = &_dut->M_AXIF_##port_id##_ARCACHE;                            \
  name.arvalid = &_dut->M_AXIF_##port_id##_ARVALID;                            \
  name.arready = &_dut->M_AXIF_##port_id##_ARREADY;                            \
  name.arqos = &_dut->M_AXIF_##port_id##_ARQOS;                                \
  name.arregion = &_dut->M_AXIF_##port_id##_ARREGION;                          \
  name.rid = &_dut->M_AXIF_##port_id##_RID;                                    \
  name.rdata = &_dut->M_AXIF_##port_id##_RDATA;                                \
  name.rresp = &_dut->M_AXIF_##port_id##_RRESP;                                \
  name.rlast = &_dut->M_AXIF_##port_id##_RLAST;                                \
  name.rvalid = &_dut->M_AXIF_##port_id##_RVALID;                              \
  name.rready = &_dut->M_AXIF_##port_id##_RREADY;

namespace demu::hal::axi {
using namespace isa;

struct AXIFullSignals {
  uint32_t *awaddr;
  uint8_t *awprot;
  uint8_t *awid;
  uint8_t *awlen;
  uint8_t *awsize;
  uint8_t *awburst;
  uint8_t *awlock;
  uint8_t *awcache;
  uint8_t *awvalid;
  uint8_t *awready;
  uint8_t *awqos;
  uint8_t *awregion;
  uint32_t *wdata;
  uint8_t *wstrb;
  uint8_t *wlast;
  uint8_t *wvalid;
  uint8_t *wready;
  uint8_t *wid;
  uint8_t *bid;
  uint8_t *bresp;
  uint8_t *bvalid;
  uint8_t *bready;
  uint32_t *araddr;
  uint8_t *arprot;
  uint8_t *arid;
  uint8_t *arlen;
  uint8_t *arsize;
  uint8_t *arburst;
  uint8_t *arlock;
  uint8_t *arcache;
  uint8_t *arvalid;
  uint8_t *arready;
  uint8_t *arqos;
  uint8_t *arregion;
  uint8_t *rid;
  uint32_t *rdata;
  uint8_t *rresp;
  uint8_t *rlast;
  uint8_t *rvalid;
  uint8_t *rready;
};

} // namespace demu::hal::axi
