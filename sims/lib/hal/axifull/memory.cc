#include "demu/hal/axifull/memory.hh"

namespace demu::hal::axi {

AXIFullMemory::AXIFullMemory(size_t size, addr_t base_addr, size_t read_delay,
                             size_t write_delay)
    : _memory(std::make_unique<Memory>(size, base_addr)),
      _read_delay(read_delay), _write_delay(write_delay) {
  reset();
}

void AXIFullMemory::reset() {
  _memory->clear();
  _write_addr_queue = {};
  _write_data_queue = {};
  _write_resp_queue = {};
  _read_addr_queue = {};
  _read_data_queue = {};
  _write_burst_active = false;
  _write_beat_count = 0;
}

void AXIFullMemory::clock_tick() {
  process_writes();
  update_delays();
  process_reads();
}

addr_t AXIFullMemory::calculate_address(addr_t base, uint8_t beat, uint8_t len,
                                        uint8_t size, uint8_t burst) const {
  uint32_t bytes_per_beat = 1u << size;

  switch (burst) {
  case 0: // FIXED
    return base;
  case 1: // INCR
    return base + (beat * bytes_per_beat);
  case 2: // WRAP
  {
    uint32_t total_bytes = (len + 1) * bytes_per_beat;
    uint32_t wrap_boundary = (base / total_bytes) * total_bytes;
    uint32_t offset =
        (base - wrap_boundary + beat * bytes_per_beat) % total_bytes;
    return wrap_boundary + offset;
  }
  default:
    return base + (beat * bytes_per_beat);
  }
}

void AXIFullMemory::process_writes() {
  // Start a new write burst
  if (!_write_burst_active && !_write_addr_queue.empty()) {
    _current_write_burst = _write_addr_queue.front();
    _write_addr_queue.pop();
    _write_burst_active = true;
    _write_beat_count = 0;
  }

  // Process write data beats
  if (_write_burst_active && !_write_data_queue.empty()) {
    const WriteDataTransaction &wdata = _write_data_queue.front();

    // Calculate address for this beat
    addr_t addr =
        calculate_address(_current_write_burst.base_addr, _write_beat_count,
                          _current_write_burst.len, _current_write_burst.size,
                          _current_write_burst.burst);

    // Write data to memory
    uint32_t bytes_per_beat = 1u << _current_write_burst.size;
    bool addr_valid = true;

    for (uint32_t i = 0; i < bytes_per_beat && i < 4; ++i) {
      if (wdata.strb & (1 << i)) {
        addr_t byte_addr = addr + i;
        if (_memory->is_valid_addr(byte_addr)) {
          byte_t byte_val = static_cast<byte_t>((wdata.data >> (i * 8)) & 0xFF);
          _memory->write_byte(byte_addr, byte_val);
        } else {
          addr_valid = false;
        }
      }
    }

    _write_data_queue.pop();

    // Check if burst is complete
    if (wdata.last || _write_beat_count >= _current_write_burst.len) {
      uint8_t resp = addr_valid ? 0u : 2u; // OKAY or SLVERR
      _write_resp_queue.push({resp, _current_write_burst.id, _write_delay});
      _write_burst_active = false;
      _write_beat_count = 0;
    } else {
      _write_beat_count++;
    }
  }
}

void AXIFullMemory::process_reads() {
  // Process new read burst request
  if (!_read_addr_queue.empty()) {
    ReadAddrTransaction read_burst = _read_addr_queue.front();
    _read_addr_queue.pop();

    // Generate all read data beats for this burst
    for (uint8_t beat = 0; beat <= read_burst.len; ++beat) {
      addr_t addr =
          calculate_address(read_burst.base_addr, beat, read_burst.len,
                            read_burst.size, read_burst.burst);

      uint32_t bytes_per_beat = 1u << read_burst.size;
      bool addr_valid = _memory->is_valid_addr(addr) &&
                        _memory->is_valid_addr(addr + bytes_per_beat - 1);

      word_t data = 0;
      if (addr_valid) {
        for (uint32_t i = 0; i < bytes_per_beat && i < 4; ++i) {
          byte_t byte_val = _memory->read_byte(addr + i);
          data |= (static_cast<word_t>(byte_val) << (i * 8));
        }
      }

      bool is_last = (beat == read_burst.len);
      _read_data_queue.push({data, (uint8_t)(addr_valid ? 0u : 2u), is_last,
                             read_burst.id, _read_delay, false});
    }
  }

  // Mark first unprocessed transaction as ready
  if (!_read_data_queue.empty() && !_read_data_queue.front().processed) {
    ReadDataTransaction &read_trans = _read_data_queue.front();
    if (read_trans.delay > 0) {
      read_trans.delay--;
    } else {
      read_trans.processed = true;
    }
  }
}

void AXIFullMemory::update_delays() {
  if (!_write_resp_queue.empty() && _write_resp_queue.front().delay > 0) {
    _write_resp_queue.front().delay--;
  }
}

// AW channel
void AXIFullMemory::aw_valid(addr_t addr, uint16_t id, uint8_t len,
                             uint8_t size, uint8_t burst, uint8_t lock,
                             uint8_t cache, uint8_t prot, uint8_t qos,
                             uint8_t region) {
  _write_addr_queue.push({addr, id, len, size, burst});
}

bool AXIFullMemory::aw_ready() const noexcept { return true; }

// W channel
void AXIFullMemory::w_valid(word_t data, byte_t strb, bool last, uint16_t id) {
  _write_data_queue.push({data, strb, last, id});
}

bool AXIFullMemory::w_ready() const noexcept { return true; }

// B channel
bool AXIFullMemory::b_valid() const noexcept {
  return !_write_resp_queue.empty() && _write_resp_queue.front().delay == 0;
}

void AXIFullMemory::b_ready(bool ready) {
  if (ready && !_write_resp_queue.empty() &&
      _write_resp_queue.front().delay == 0) {
    _write_resp_queue.pop();
  }
}

uint8_t AXIFullMemory::b_resp() const noexcept {
  return _write_resp_queue.empty() ? 0 : _write_resp_queue.front().resp;
}

uint16_t AXIFullMemory::b_id() const noexcept {
  return _write_resp_queue.empty() ? 0 : _write_resp_queue.front().id;
}

// AR channel
void AXIFullMemory::ar_valid(addr_t addr, uint16_t id, uint8_t len,
                             uint8_t size, uint8_t burst, uint8_t lock,
                             uint8_t cache, uint8_t prot, uint8_t qos,
                             uint8_t region) {
  _read_addr_queue.push({addr, id, len, size, burst});
}

bool AXIFullMemory::ar_ready() const noexcept { return true; }

// R channel
bool AXIFullMemory::r_valid() const noexcept {
  return !_read_data_queue.empty() && _read_data_queue.front().processed;
}

void AXIFullMemory::r_ready(bool ready) {
  if (ready && !_read_data_queue.empty() &&
      _read_data_queue.front().processed) {
    _read_data_queue.pop();
  }
}

word_t AXIFullMemory::r_data() const noexcept {
  return _read_data_queue.empty() ? 0 : _read_data_queue.front().data;
}

uint8_t AXIFullMemory::r_resp() const noexcept {
  return _read_data_queue.empty() ? 0 : _read_data_queue.front().resp;
}

bool AXIFullMemory::r_last() const noexcept {
  return _read_data_queue.empty() ? false : _read_data_queue.front().last;
}

uint16_t AXIFullMemory::r_id() const noexcept {
  return _read_data_queue.empty() ? 0 : _read_data_queue.front().id;
}

} // namespace demu::hal::axi
