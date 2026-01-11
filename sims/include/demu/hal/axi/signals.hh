#pragma once

#include "../../isa/isa.hh"
#include <cstdint>

namespace demu::hal {
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

} // namespace demu::hal
