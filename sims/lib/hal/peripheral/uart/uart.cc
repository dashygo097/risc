#include "demu/hal/peripheral/uart/uart.hh"

namespace demu::hal::uart {

void UART::reset() {
  allocator_->clear();
  allocator_->write_word(base_address() + UART_TXC, 0x00000000);
}

void UART::clock_tick() {
  // placeholder
}

void UART::dump(addr_t start, size_t size) const noexcept {
  const addr_t base = base_address();
  const addr_t end = start + size;

  auto overlaps = [&](addr_t reg_start, size_t reg_size) -> bool {
    return reg_start < end && (reg_start + reg_size) > start;
  };

  bool printed_header = false;
  auto print_header_once = [&]() {
    if (!printed_header) {
      HAL_INFO("--- UART Registers Dump [0x{:08X} - 0x{:08X}] ---", start, end);
      printed_header = true;
    }
  };

  if (overlaps(base + UART_TXC, 4)) {
    print_header_once();
    uint32_t txc = allocator_->read_word(base + UART_TXC);
    HAL_INFO("  TXCTRL   : 0x{:08x}", txc);
  }

  if (overlaps(base + UART_BAUDIV, 4)) {
    print_header_once();
    uint32_t div = allocator_->read_word(base + UART_BAUDIV);
    HAL_INFO("  BAUDIV   : 0x{:08x}", div);
  }

  if (printed_header) {
    HAL_INFO("--------------------------------------------------");
  }
}

} // namespace demu::hal::uart
