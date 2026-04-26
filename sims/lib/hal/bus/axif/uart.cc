#include "demu/hal/bus/axif/uart.hh"

namespace demu::hal::axif {

void AXIFullUART::reset() {
  uart_->reset();

  _write_req_queue = std::queue<BurstTransaction>();
  _write_data_queue = std::queue<WriteData>();
  _write_resp_queue = std::queue<WriteResponse>();
  _read_req_queue = std::queue<BurstTransaction>();
  _read_data_queue = std::queue<ReadData>();

  pin_awvalid = false;
  pin_wvalid = false;
  pin_bready = false;
  pin_arvalid = false;
  pin_rready = false;
}

void AXIFullUART::clock_tick() {
  uart_->clock_tick();

  if (pin_awvalid && aw_ready()) {
    _write_req_queue.push(
        {pin_awid, pin_awaddr, pin_awlen, pin_awsize, pin_awburst, 0});
  }
  if (pin_wvalid && w_ready()) {
    _write_data_queue.push({pin_wdata, pin_wstrb, pin_wlast});
  }
  if (pin_bready && b_valid()) {
    _write_resp_queue.pop();
  }
  if (pin_arvalid && ar_ready()) {
    _read_req_queue.push(
        {pin_arid, pin_araddr, pin_arlen, pin_arsize, pin_arburst, 0});
  }
  if (pin_rready && r_valid()) {
    _read_data_queue.pop();
  }

  process_writes();
  process_reads();
}

void AXIFullUART::calculate_next_address(BurstTransaction &req) {
  if (req.burst == 1 || req.burst == 2) {
    req.addr += (1u << req.size);
  }
}

void AXIFullUART::process_writes() {
  if (_write_req_queue.empty() || _write_data_queue.empty()) {
    return;
  }

  BurstTransaction &req = _write_req_queue.front();
  const WriteData wdata = _write_data_queue.front();
  _write_data_queue.pop();

  const bool valid = owns_address(req.addr);

  if (valid) {
    addr_t offset = to_offset(req.addr);

    if (offset == uart::UART_TXD) {
      char c = static_cast<char>(wdata.data & 0xFF);
      std::cout << c << std::flush;
    } else {
      for (int i = 0; i < 4; ++i) {
        if (wdata.strb & (1u << i)) {
          allocator()->write_byte(
              req.addr + i,
              static_cast<byte_t>((wdata.data >> (i * 8)) & 0xFF));
        }
      }
    }
  }

  req.beats++;
  calculate_next_address(req);

  if (wdata.last || req.beats > req.len) {
    _write_resp_queue.push({req.id, static_cast<uint8_t>(valid ? 0 : 2)});
    _write_req_queue.pop();
  }
}

void AXIFullUART::process_reads() {
  if (_read_req_queue.empty()) {
    return;
  }

  BurstTransaction &req = _read_req_queue.front();
  const bool valid = owns_address(req.addr);

  word_t data = valid ? allocator()->read_word(req.addr) : 0u;

  const bool last = (req.beats == req.len);
  _read_data_queue.push(
      {req.id, data, static_cast<uint8_t>(valid ? 0 : 2), last});

  req.beats++;
  calculate_next_address(req);

  if (last) {
    _read_req_queue.pop();
  }
}

void AXIFullUART::dump(addr_t start, size_t size) const noexcept {
  uart_->dump(start, size);
}

} // namespace demu::hal::axif
