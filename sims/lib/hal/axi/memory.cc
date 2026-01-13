#include "demu/hal/axi/memory.hh"

namespace demu::hal::axi {

AXIMemory::AXIMemory(size_t size, addr_t base_addr, size_t read_delay,
                     size_t write_delay)
    : _memory(std::make_unique<Memory>(size, base_addr)),
      _read_delay(read_delay), _write_delay(write_delay) {
  reset();
}

void AXIMemory::reset() {
  _memory->clear();

  // Clear all queues
  _write_addr_queue = {};
  _write_data_queue = {};
  _write_resp_queue = {};
  _read_queue = {};
}

void AXIMemory::clock_tick() {
  process_writes();
  update_delays();
  process_reads();
}

void AXIMemory::process_writes() {
  if (!_write_addr_queue.empty() && !_write_data_queue.empty()) {
    const addr_t addr = _write_addr_queue.front();
    const WriteData &wdata = _write_data_queue.front();

    _write_addr_queue.pop();
    _write_data_queue.pop();

    const bool addr_valid =
        _memory->is_valid_addr(addr) && _memory->is_valid_addr(addr + 3);

    if (addr_valid) {
      for (int i = 0; i < 4; ++i) {
        if (wdata.strb & (1 << i)) {
          const byte_t byte_val =
              static_cast<byte_t>((wdata.data >> (i * 8)) & 0xFF);
          _memory->write_byte(addr + i, byte_val);
        }
      }
    }

    uint8_t resp = addr_valid ? 0u : 2u; // OKAY or SLVERR
    _write_resp_queue.push({resp, _write_delay});
  }
}

void AXIMemory::process_reads() {
  if (!_read_queue.empty() && !_read_queue.front().processed) {
    ReadTransaction &read_trans = _read_queue.front();

    if (read_trans.delay > 0) {
      read_trans.delay--;
    } else {
      const bool addr_valid = _memory->is_valid_addr(read_trans.addr) &&
                              _memory->is_valid_addr(read_trans.addr + 3);

      read_trans.data = addr_valid ? _memory->read_word(read_trans.addr) : 0;
      read_trans.processed = true;
    }
  }
}

void AXIMemory::update_delays() {
  if (!_write_resp_queue.empty() && _write_resp_queue.front().delay > 0) {
    _write_resp_queue.front().delay--;
  }
}

// AW
void AXIMemory::aw_valid(addr_t addr) { _write_addr_queue.push(addr); }

bool AXIMemory::aw_ready() const noexcept { return true; }

// W
void AXIMemory::w_valid(word_t data, byte_t strb) {
  _write_data_queue.push({data, strb});
}

bool AXIMemory::w_ready() const noexcept { return true; }

// B
bool AXIMemory::b_valid() const noexcept {
  return !_write_resp_queue.empty() && _write_resp_queue.front().delay == 0;
}

void AXIMemory::b_ready(bool ready) {
  if (ready && !_write_resp_queue.empty() &&
      _write_resp_queue.front().delay == 0) {
    _write_resp_queue.pop();
  }
}

uint8_t AXIMemory::b_resp() const noexcept {
  return _write_resp_queue.empty() ? 0 : _write_resp_queue.front().resp;
}

// AR
void AXIMemory::ar_valid(addr_t addr) {
  _read_queue.push({addr, 0, false, _read_delay});
}

bool AXIMemory::ar_ready() const noexcept { return true; }

// R
bool AXIMemory::r_valid() const noexcept {
  return !_read_queue.empty() && _read_queue.front().processed;
}

void AXIMemory::r_ready(bool ready) {
  if (ready && !_read_queue.empty() && _read_queue.front().processed) {
    _read_queue.pop();
  }
}

word_t AXIMemory::r_data() const noexcept {
  return _read_queue.empty() ? 0 : _read_queue.front().data;
}

uint8_t AXIMemory::r_resp() const noexcept {
  return 0; // OKAY
}

} // namespace demu::hal::axi
