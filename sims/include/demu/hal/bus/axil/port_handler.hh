#pragma once

#include "../../port_handler.hh"
#include "./slave.hh"
#include <functional>

#define MAP_AXIL_SIGNALS(dut, name, port_id)                                   \
  name.awaddr = &dut->M_AXIL_##port_id##_AWADDR;                               \
  name.awprot = &dut->M_AXIL_##port_id##_AWPROT;                               \
  name.awvalid = &dut->M_AXIL_##port_id##_AWVALID;                             \
  name.awready = &dut->M_AXIL_##port_id##_AWREADY;                             \
  name.wdata = &dut->M_AXIL_##port_id##_WDATA;                                 \
  name.wstrb = &dut->M_AXIL_##port_id##_WSTRB;                                 \
  name.wvalid = &dut->M_AXIL_##port_id##_WVALID;                               \
  name.wready = &dut->M_AXIL_##port_id##_WREADY;                               \
  name.bresp = &dut->M_AXIL_##port_id##_BRESP;                                 \
  name.bvalid = &dut->M_AXIL_##port_id##_BVALID;                               \
  name.bready = &dut->M_AXIL_##port_id##_BREADY;                               \
  name.araddr = &dut->M_AXIL_##port_id##_ARADDR;                               \
  name.arprot = &dut->M_AXIL_##port_id##_ARPROT;                               \
  name.arvalid = &dut->M_AXIL_##port_id##_ARVALID;                             \
  name.arready = &dut->M_AXIL_##port_id##_ARREADY;                             \
  name.rdata = &dut->M_AXIL_##port_id##_RDATA;                                 \
  name.rresp = &dut->M_AXIL_##port_id##_RRESP;                                 \
  name.rvalid = &dut->M_AXIL_##port_id##_RVALID;                               \
  name.rready = &dut->M_AXIL_##port_id##_RREADY;

namespace demu::hal::axi {

struct AXILiteSignals {
  addr_t *awaddr;
  uint8_t *awprot;
  uint8_t *awvalid;
  uint8_t *awready;
  word_t *wdata;
  uint8_t *wstrb;
  uint8_t *wvalid;
  uint8_t *wready;
  uint8_t *bresp;
  uint8_t *bvalid;
  uint8_t *bready;
  addr_t *araddr;
  uint8_t *arprot;
  uint8_t *arvalid;
  uint8_t *arready;
  word_t *rdata;
  uint8_t *rresp;
  uint8_t *rvalid;
  uint8_t *rready;
};

class AXILitePortHandler final : public hal::PortHandler {
public:
  using SignalProvider = std::function<AXILiteSignals()>;

  explicit AXILitePortHandler(SignalProvider provider)
      : provider_(std::move(provider)) {}

  void handle(hal::Hardware *hw) noexcept override {
    auto *slave = dynamic_cast<AXILiteSlave *>(hw);
    if (!slave) {
      return;
    }

    auto s = provider_();

    // AW
    if (*s.awvalid && slave->aw_ready()) {
      slave->aw_valid(*s.awaddr);
    }
    *s.awready = slave->aw_ready();

    // W
    if (*s.wvalid && slave->w_ready()) {
      slave->w_valid(*s.wdata, *s.wstrb & 0xF);
    }
    *s.wready = slave->w_ready();

    // B
    *s.bvalid = slave->b_valid();
    *s.bresp = slave->b_resp();
    slave->b_ready(*s.bready);

    // AR
    if (*s.arvalid && slave->ar_ready()) {
      slave->ar_valid(*s.araddr);
    }
    *s.arready = slave->ar_ready();

    // R
    *s.rvalid = slave->r_valid();
    *s.rdata = slave->r_data();
    *s.rresp = slave->r_resp();
    slave->r_ready(*s.rready);
  }

  [[nodiscard]] auto protocol_name() const noexcept -> const char * override {
    return "AXI4-Lite";
  }

private:
  SignalProvider provider_;
};

} // namespace demu::hal::axi
