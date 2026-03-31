#pragma once

#include "../../device.hh"

namespace demu::hal::uart {
using namespace isa;

enum {
  UART_RXD = 0x00,
  UART_TXD = 0x04,
  UART_TXC = 0x08,
  UART_RXC = 0x0C,
  UART_BAUDIV = 0x10,
};

class Uart final : public Device {
  explicit Uart(risc::DeviceDescriptor desc)
      : Device(desc),
        memory_(std::make_unique<MemoryAllocator>(desc.base(), desc.size())) {}

  ~Uart() override = default;

  [[nodiscard]] auto allocator() const noexcept -> MemoryAllocator * override {
    return memory_.get();
  }

  void clock_tick() override;
  void reset() override;
  void dump(addr_t start, size_t size) const noexcept override;

private:
  std::unique_ptr<MemoryAllocator> memory_;
};

} // namespace demu::hal::uart
