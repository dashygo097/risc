#include "demu/hal/bus/axif/sram.hh"
#include <algorithm>
#include <iomanip>
#include <sstream>

namespace demu::hal::axi {

void AXIFullSRAM::reset() {
  sram_->reset();

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

void AXIFullSRAM::clock_tick() {
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

void AXIFullSRAM::calculate_next_address(BurstTransaction &req) {
  const addr_t bytes_per_beat = 1u << req.size;

  if (req.burst == 1) {
    // INCR Burst: Purely linear increment
    req.addr += bytes_per_beat;
  } else if (req.burst == 2) {
    // WRAP Burst: Wraps around when hitting the upper boundary
    const addr_t wrap_bytes =
        (static_cast<addr_t>(req.len) + 1u) * bytes_per_beat;
    const addr_t wrap_mask = wrap_bytes - 1u;

    const addr_t base_addr = req.addr & ~wrap_mask;

    const addr_t next_offset = (req.addr + bytes_per_beat) & wrap_mask;

    req.addr = base_addr | next_offset;
  }
}

void AXIFullSRAM::process_writes() {
  if (_write_req_queue.empty() || _write_data_queue.empty()) {
    return;
  }

  BurstTransaction &req = _write_req_queue.front();
  const WriteData wdata = _write_data_queue.front();
  _write_data_queue.pop();

  const bool valid = owns_address(req.addr);

  if (valid) {
    for (int i = 0; i < 4; ++i) {
      if (wdata.strb & (1u << i)) {
        allocator()->write_byte(
            req.addr + i, static_cast<byte_t>((wdata.data >> (i * 8)) & 0xFF));
      }
    }
  }

  req.beats_completed++;
  calculate_next_address(req);

  if (wdata.last || req.beats_completed > req.len) {
    _write_resp_queue.push(
        {req.id,
         static_cast<uint8_t>(valid ? 0 : 2)}); // OKAY (0) or SLVERR (2)
    _write_req_queue.pop();
  }
}

void AXIFullSRAM::process_reads() {
  if (_read_req_queue.empty()) {
    return;
  }

  BurstTransaction &req = _read_req_queue.front();

  const bool valid = owns_address(req.addr);
  word_t data = valid ? allocator()->read_word(req.addr) : 0u;

  const bool last = (req.beats_completed == req.len);

  _read_data_queue.push(
      {req.id, data, static_cast<uint8_t>(valid ? 0 : 2), last});

  req.beats_completed++;
  calculate_next_address(req);

  if (last) {
    _read_req_queue.pop();
  }
}

void AXIFullSRAM::dump(addr_t start, size_t size) const noexcept {
  if (!owns_address(start)) {
    HAL_WARN("AXIFullSRAM dump: 0x{:08X} not owned",
             static_cast<uint64_t>(start));
    return;
  }
  const size_t clamped = std::min(
      size, static_cast<size_t>(base_address() + address_range() - start));

  HAL_INFO("SRAM dump [0x{:08X} - 0x{:08X}]:", static_cast<uint64_t>(start),
           static_cast<uint64_t>(start + clamped));

  const byte_t *ptr = allocator()->get_ptr(start);
  if (!ptr) {
    return;
  }

  for (size_t i = 0; i < clamped; i += 16) {
    std::stringstream ss;
    ss << std::hex << std::setw(8) << std::setfill('0') << (start + i) << ": ";
    for (size_t j = 0; j < 16; ++j) {
      if (i + j < clamped) {
        ss << std::hex << std::setw(2) << std::setfill('0')
           << static_cast<int>(ptr[i + j]) << ' ';
      } else {
        ss << "   ";
      }
      if (j == 7) {
        ss << ' ';
      }
    }
    ss << " |";
    for (size_t j = 0; j < 16 && (i + j) < clamped; ++j) {
      const byte_t c = ptr[i + j];
      ss << static_cast<char>((c >= 32 && c < 127) ? c : '.');
    }
    ss << '|';
    HAL_INFO("{}", ss.str());
  }
}

} // namespace demu::hal::axi
