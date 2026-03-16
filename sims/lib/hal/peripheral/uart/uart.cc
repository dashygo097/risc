#include "demu/hal/peripheral/uart/uart.hh"
#include "demu/logger.hh"
#include <cassert>
#include <cstdio>

namespace demu::hal::uart {

Uart::Uart(addr_t base_addr, size_t size, size_t tx_fifo_depth,
           size_t rx_fifo_depth)
    : regs_(std::make_unique<MemoryAllocator>(base_addr, size)),
      tx_fifo_depth_(tx_fifo_depth), rx_fifo_depth_(rx_fifo_depth) {
  regs_->clear();
  sync_status();
}

void Uart::reset() {
  regs_->clear();
  tx_fifo_ = {};
  rx_fifo_ = {};
  sync_status();
  HAL_DEBUG("UART @ 0x{:08X} reset.", static_cast<uint64_t>(base_address()));
}

void Uart::clock_tick() {
  drain_tx();
  sync_status();
}

word_t Uart::read_reg(addr_t offset) noexcept {
  switch (offset) {
  case UART_REG_DATA: {
    if (rx_fifo_.empty()) {
      HAL_DEBUG("UART DATA read on empty RX FIFO – returning 0.");
      return 0u;
    }
    const uint8_t byte = rx_fifo_.front();
    rx_fifo_.pop();
    sync_status();
    return static_cast<word_t>(byte);
  }

  case UART_REG_STATUS:
    return reg_read(UART_REG_STATUS);

  case UART_REG_CTRL:
    return reg_read(UART_REG_CTRL);

  case UART_REG_BAUD:
    return reg_read(UART_REG_BAUD);

  default:
    HAL_WARN("UART read from unknown offset 0x{:02X}",
             static_cast<uint32_t>(offset));
    return 0u;
  }
}

void Uart::write_reg(addr_t offset, word_t data, byte_t strb) noexcept {
  switch (offset) {
  case UART_REG_DATA: {
    if (!(strb & 0x1u))
      break;
    const uint8_t ch = static_cast<uint8_t>(data & 0xFFu);
    if (tx_fifo_.size() < tx_fifo_depth_) {
      tx_fifo_.push(ch);
    } else {
      HAL_WARN("UART TX FIFO full – byte 0x{:02X} dropped.", ch);
    }
    sync_status();
    break;
  }

  case UART_REG_STATUS:
    HAL_DEBUG("UART: write to read-only STATUS ignored.");
    break;

  case UART_REG_CTRL:
    reg_write(UART_REG_CTRL, data, strb);
    break;

  case UART_REG_BAUD:
    reg_write(UART_REG_BAUD, data, strb);
    break;

  default:
    HAL_WARN("UART write to unknown offset 0x{:02X} = 0x{:08X}",
             static_cast<uint32_t>(offset), static_cast<uint32_t>(data));
    break;
  }
}

bool Uart::rx_push(uint8_t byte) {
  if (rx_fifo_.size() >= rx_fifo_depth_) {
    HAL_WARN("UART RX FIFO overflow – byte 0x{:02X} dropped.", byte);
    return false;
  }
  rx_fifo_.push(byte);
  sync_status();
  return true;
}

void Uart::dump() const noexcept {
  HAL_INFO("Uart @ 0x{:08X}:", static_cast<uint64_t>(base_address()));
  HAL_INFO("  STATUS : 0x{:08X}", reg_read(UART_REG_STATUS));
  HAL_INFO("  CTRL   : 0x{:08X}", reg_read(UART_REG_CTRL));
  HAL_INFO("  BAUD   : 0x{:08X}", reg_read(UART_REG_BAUD));
  HAL_INFO("  TX FIFO: {}/{}", tx_fifo_.size(), tx_fifo_depth_);
  HAL_INFO("  RX FIFO: {}/{}", rx_fifo_.size(), rx_fifo_depth_);
  HAL_INFO("  Allocator contents:");
  regs_->dump(base_address(), UART_ADDR_RANGE);
}

uint32_t Uart::reg_read(addr_t offset) const noexcept {
  const addr_t abs = base_address() + offset;
  uint32_t val = 0;
  for (int i = 0; i < 4; ++i) {
    val |= static_cast<uint32_t>(regs_->read_byte(abs + i)) << (i * 8);
  }
  return val;
}

void Uart::reg_write(addr_t offset, uint32_t val, byte_t strb) noexcept {
  const addr_t abs = base_address() + offset;
  for (int i = 0; i < 4; ++i) {
    if (strb & (1u << i)) {
      regs_->write_byte(abs + i, static_cast<byte_t>((val >> (i * 8)) & 0xFF));
    }
  }
}

void Uart::sync_status() noexcept {
  uint32_t status = 0;
  if (!rx_fifo_.empty())
    status |= UART_STATUS_RX_READY;
  if (tx_fifo_.size() < tx_fifo_depth_)
    status |= UART_STATUS_TX_READY;
  if (tx_fifo_.empty())
    status |= UART_STATUS_TX_EMPTY;

  reg_write(UART_REG_STATUS, status, 0xF);
}

void Uart::drain_tx() {
  if (tx_fifo_.empty())
    return;
  const uint8_t ch = tx_fifo_.front();
  tx_fifo_.pop();
  if (echo_stdout_)
    ::write(STDOUT_FILENO, &ch, 1);
  if (tx_callback_)
    tx_callback_(ch);
}

} // namespace demu::hal::uart
