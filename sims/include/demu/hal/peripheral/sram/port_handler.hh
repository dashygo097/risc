#pragma once

#include "../../port_handler.hh"
#include "./signals.hh"
#include "./sram.hh"

namespace demu::hal::sram {

template <typename T> class SRAMPortHandler final : public hal::PortHandler {
  using SignalProvider = std::function<CacheSignals<T>()>;

  explicit SRAMPortHandler(SignalProvider provider)
      : provider_(std::move(provider)) {}

  void handle(hal::Hardware *hw) noexcept override {
    auto *sram = dynamic_cast<SRAM *>(hw);
    if (!sram)
      return;

    auto s = provider_();

    *s.req.ready = 1;  // Always ready
    *s.resp.valid = 1; // Always valid (for simplicity)
    if (*s.req.valid) {
      if (*s.req.op == 0) {
        // Read
        size_t size = sizeof(T);
        for (size_t i = 0; i < size; i++) {
          *s.resp.data = sram->memory().read<T>(*s.req.addr + i);
        }
      } else {
        // Write
        size_t size = sizeof(T);
        for (size_t i = 0; i < size; i++) {
          sram->memory().write<T>(*s.req.addr + i, *s.req.data);
        }
      }
    }
  }

  const char *protocol_name() const noexcept override { return "SRAM"; }

private:
  SignalProvider provider_;
};

} // namespace demu::hal::sram
