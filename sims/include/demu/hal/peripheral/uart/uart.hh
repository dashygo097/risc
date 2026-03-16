#pragma once

#include "../../allocator.hh"
#include "../../hardware.hh"
#include <cstdint>
#include <functional>
#include <memory>
#include <queue>

namespace demu::hal::uart {

using namespace isa;

// Register map
inline constexpr size_t UART_ADDR_RANGE = 0x10u; //< 16 bytes total
inline constexpr addr_t UART_REG_DATA = 0x00u;   //< TX write / RX read
inline constexpr addr_t UART_REG_STATUS = 0x04u; //< Read-only status flags
inline constexpr addr_t UART_REG_CTRL = 0x08u;   //< Control / IRQ-enable
inline constexpr addr_t UART_REG_BAUD = 0x0Cu;   //< Baud-rate divisor

// STATUS register bit-fields
inline constexpr uint32_t UART_STATUS_RX_READY =
    (1u << 0); //< RX FIFO non-empty
inline constexpr uint32_t UART_STATUS_TX_READY = (1u << 1); //< TX FIFO not full
inline constexpr uint32_t UART_STATUS_TX_EMPTY = (1u << 2); //< TX FIFO empty

// CTRL register bit-fields
inline constexpr uint32_t UART_CTRL_RX_IRQ_EN =
    (1u << 0); //< Enable RX interrupt
inline constexpr uint32_t UART_CTRL_TX_IRQ_EN =
    (1u << 1); //< Enable TX-empty interrupt

class UARTDevice : public Hardware {
public:
  explicit UARTDevice(addr_t base_addr, size_t size, size_t tx_fifo_depth = 16,
                      size_t rx_fifo_depth = 16);

  ~UARTDevice() override = default;

  UARTDevice(const UARTDevice &) = delete;
  UARTDevice &operator=(const UARTDevice &) = delete;
  UARTDevice(UARTDevice &&) = default;

  [[nodiscard]] addr_t base_address() const noexcept override {
    return regs_->base_address();
  }

  [[nodiscard]] size_t address_range() const noexcept override {
    return regs_->size();
  }

  [[nodiscard]] const char *name() const noexcept override { return "UART"; }

  void clock_tick() override;
  void reset() override;

  [[nodiscard]] word_t read_reg(addr_t offset) noexcept;

  void write_reg(addr_t offset, word_t data, byte_t strb = 0xFu) noexcept;

  bool rx_push(uint8_t byte);

  [[nodiscard]] bool irq_pending() const noexcept;

  void set_tx_callback(std::function<void(uint8_t)> cb) {
    tx_callback_ = std::move(cb);
  }

  void set_echo_stdout(bool enable) noexcept { echo_stdout_ = enable; }
  void dump() const noexcept;
  void dump(addr_t /*start*/, size_t /*size*/) const noexcept override {
    dump();
  }

private:
  // components
  std::unique_ptr<MemoryAllocator> regs_;

  std::queue<uint8_t> tx_fifo_;
  std::queue<uint8_t> rx_fifo_;

  // states
  const size_t tx_fifo_depth_;
  const size_t rx_fifo_depth_;

  bool echo_stdout_ = false;
  std::function<void(uint8_t)> tx_callback_;

  // helpers
  [[nodiscard]] uint32_t reg_read(addr_t offset) const noexcept;
  void reg_write(addr_t offset, uint32_t val, byte_t strb) noexcept;
  void sync_status() noexcept;
  void drain_tx();
};

} // namespace demu::hal::uart
