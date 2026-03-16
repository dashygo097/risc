#include "demu/hal/bus/axil/uart.hh"
#include "demu/logger.hh"

namespace demu::hal::axi {

AXILiteUart::AXILiteUart(addr_t base_addr, size_t size, size_t tx_fifo_depth,
                         size_t rx_fifo_depth, size_t read_delay,
                         size_t write_delay)
    : uart_(std::make_unique<uart::Uart>(base_addr, size, tx_fifo_depth,
                                         rx_fifo_depth)),
      read_delay_(read_delay), write_delay_(write_delay) {}

void AXILiteUart::reset() {
  uart_->reset();
  _write_addr_queue = {};
  _write_data_queue = {};
  _write_resp_queue = {};
  _read_queue = {};
}

void AXILiteUart::clock_tick() {
  process_writes();
  update_delays();
  process_reads();
  uart_->clock_tick();
}

void AXILiteUart::process_writes() {
  if (_write_addr_queue.empty() || _write_data_queue.empty())
    return;

  const addr_t addr = _write_addr_queue.front();
  const WriteData &wdata = _write_data_queue.front();
  _write_addr_queue.pop();
  _write_data_queue.pop();

  const bool addr_valid = uart_->owns_address(addr);

  if (addr_valid) {
    uart_->write_reg(uart_->to_offset(addr), wdata.data, wdata.strb);
  } else {
    HAL_WARN("AXILiteUart: write to out-of-range address 0x{:08X}",
             static_cast<uint64_t>(addr));
  }

  const uint8_t resp = addr_valid ? 0u : 2u;
  _write_resp_queue.push({resp, write_delay_});
}

void AXILiteUart::process_reads() {
  if (_read_queue.empty() || _read_queue.front().processed)
    return;

  ReadTransaction &rt = _read_queue.front();

  if (rt.delay > 0) {
    --rt.delay;
    return;
  }

  const bool addr_valid = uart_->owns_address(rt.addr);

  if (addr_valid) {
    rt.data = uart_->read_reg(uart_->to_offset(rt.addr));
  } else {
    HAL_WARN("AXILiteUart: read from out-of-range address 0x{:08X}",
             static_cast<uint64_t>(rt.addr));
    rt.data = 0u;
  }

  rt.processed = true;
}

void AXILiteUart::update_delays() {
  if (!_write_resp_queue.empty() && _write_resp_queue.front().delay > 0) {
    --_write_resp_queue.front().delay;
  }
}

// AW
void AXILiteUart::aw_valid(addr_t addr) { _write_addr_queue.push(addr); }
bool AXILiteUart::aw_ready() const noexcept { return true; }

// W
void AXILiteUart::w_valid(word_t data, byte_t strb) {
  _write_data_queue.push({data, strb});
}
bool AXILiteUart::w_ready() const noexcept { return true; }

// B
bool AXILiteUart::b_valid() const noexcept {
  return !_write_resp_queue.empty() && _write_resp_queue.front().delay == 0;
}

void AXILiteUart::b_ready(bool ready) {
  if (ready && b_valid()) {
    _write_resp_queue.pop();
  }
}

uint8_t AXILiteUart::b_resp() const noexcept {
  return _write_resp_queue.empty() ? 0u : _write_resp_queue.front().resp;
}

// AR
void AXILiteUart::ar_valid(addr_t addr) {
  _read_queue.push({addr, /*data=*/0u, /*processed=*/false, read_delay_});
}
bool AXILiteUart::ar_ready() const noexcept { return true; }

// R
bool AXILiteUart::r_valid() const noexcept {
  return !_read_queue.empty() && _read_queue.front().processed;
}

void AXILiteUart::r_ready(bool ready) {
  if (ready && r_valid()) {
    _read_queue.pop();
  }
}

word_t AXILiteUart::r_data() const noexcept {
  return _read_queue.empty() ? 0u : _read_queue.front().data;
}

uint8_t AXILiteUart::r_resp() const noexcept {
  return _read_queue.empty()
             ? 0u
             : (uart_->owns_address(_read_queue.front().addr) ? 0u : 2u);
}

void AXILiteUart::dump(addr_t start, size_t size) const noexcept {
  if (!owns_address(start)) {
    HAL_WARN(
        "AXILiteUart dump: start address 0x{:08X} not owned by this device",
        static_cast<uint64_t>(start));
    return;
  }
  HAL_INFO("AXILiteUart @ 0x{:08X} – pending W:{} R:{}",
           static_cast<uint64_t>(base_address()), _write_resp_queue.size(),
           _read_queue.size());
  uart_->dump(start, size);
}

} // namespace demu::hal::axi
