#pragma once

#include "../../port_handler.hh"
#include "./signals.hh"
#include "./slave.hh"
#include <functional>

namespace demu::hal::axi {

class AXILitePortHandler final : public hal::PortHandler {
public:
  using SignalProvider = std::function<AXILiteSignals()>;

  explicit AXILitePortHandler(SignalProvider provider)
      : provider_(std::move(provider)) {}

  void handle(hal::Hardware *hw) noexcept override {
    auto *slave = dynamic_cast<AXILiteSlave *>(hw);
    if (!slave)
      return;

    auto s = provider_();

    // AW
    if (*s.awvalid && slave->aw_ready())
      slave->aw_valid(*s.awaddr);
    *s.awready = slave->aw_ready();

    // W
    if (*s.wvalid && slave->w_ready())
      slave->w_valid(*s.wdata, *s.wstrb & 0xF);
    *s.wready = slave->w_ready();

    // B
    *s.bvalid = slave->b_valid();
    *s.bresp = slave->b_resp();
    slave->b_ready(*s.bready);

    // AR
    if (*s.arvalid && slave->ar_ready())
      slave->ar_valid(*s.araddr);
    *s.arready = slave->ar_ready();

    // R
    *s.rvalid = slave->r_valid();
    *s.rdata = slave->r_data();
    *s.rresp = slave->r_resp();
    slave->r_ready(*s.rready);
  }

  const char *protocol_name() const noexcept override { return "AXI4-Lite"; }

private:
  SignalProvider provider_;
};

} // namespace demu::hal::axi
