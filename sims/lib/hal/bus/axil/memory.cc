#include "demu/hal/bus/axil/memory.hh"
#include <iomanip>
#include <sstream>

namespace demu::hal::axi {

void AXILiteMemory::reset() {
  memory_allocator_->clear();

  // Clear all queues
  _write_addr_queue = {};
  _write_data_queue = {};
  _write_resp_queue = {};
  _read_queue = {};
}

void AXILiteMemory::clock_tick() {
  process_writes();
  update_delays();
  process_reads();
}

void AXILiteMemory::dump(addr_t start, size_t size) const noexcept {
  if (!owns_address(start)) {
    HAL_WARN("dump: start address 0x{:08X} not owned by this device", start);
    return;
  }
  const addr_t owned_end = base_address() + address_range();
  const size_t clamped = (start + size > owned_end)
                             ? static_cast<size_t>(owned_end - start)
                             : size;

  HAL_INFO("Memory dump [0x{:08X} - 0x{:08X}]:", static_cast<uint64_t>(start),
           static_cast<uint64_t>(start + clamped));

  const byte_t *ptr = memory_allocator_->get_ptr(start);
  if (!ptr)
    return;

  for (size_t i = 0; i < clamped; i += 16) {
    std::stringstream ss;
    ss << std::hex << std::setw(8) << std::setfill('0') << (start + i) << ": ";

    for (size_t j = 0; j < 16; ++j) {
      if (i + j < clamped)
        ss << std::hex << std::setw(2) << std::setfill('0')
           << static_cast<int>(ptr[i + j]) << ' ';
      else
        ss << "   ";
      if (j == 7)
        ss << ' ';
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

void AXILiteMemory::process_writes() {
  if (!_write_addr_queue.empty() && !_write_data_queue.empty()) {
    const addr_t addr = _write_addr_queue.front();
    const WriteData &wdata = _write_data_queue.front();

    _write_addr_queue.pop();
    _write_data_queue.pop();

    const bool addr_valid = memory_allocator_->is_valid_addr(addr) &&
                            memory_allocator_->is_valid_addr(addr + 3);

    if (addr_valid) {
      for (int i = 0; i < 4; ++i) {
        if (wdata.strb & (1 << i)) {
          const byte_t byte_val =
              static_cast<byte_t>((wdata.data >> (i * 8)) & 0xFF);
          memory_allocator_->write_byte(addr + i, byte_val);
        }
      }
    }

    uint8_t resp = addr_valid ? 0u : 2u; // OKAY or SLVERR
    _write_resp_queue.push({resp, write_delay_});
  }
}

void AXILiteMemory::process_reads() {
  if (!_read_queue.empty() && !_read_queue.front().processed) {
    ReadTransaction &read_trans = _read_queue.front();

    if (read_trans.delay > 0) {
      read_trans.delay--;
    } else {
      const bool addr_valid =
          memory_allocator_->is_valid_addr(read_trans.addr) &&
          memory_allocator_->is_valid_addr(read_trans.addr + 3);

      read_trans.data =
          addr_valid ? memory_allocator_->read_word(read_trans.addr) : 0;
      read_trans.processed = true;
    }
  }
}

void AXILiteMemory::update_delays() {
  if (!_write_resp_queue.empty() && _write_resp_queue.front().delay > 0) {
    _write_resp_queue.front().delay--;
  }
}

// AW
void AXILiteMemory::aw_valid(addr_t addr) { _write_addr_queue.push(addr); }

bool AXILiteMemory::aw_ready() const noexcept { return true; }

// W
void AXILiteMemory::w_valid(word_t data, byte_t strb) {
  _write_data_queue.push({data, strb});
}

bool AXILiteMemory::w_ready() const noexcept { return true; }

// B
bool AXILiteMemory::b_valid() const noexcept {
  return !_write_resp_queue.empty() && _write_resp_queue.front().delay == 0;
}

void AXILiteMemory::b_ready(bool ready) {
  if (ready && !_write_resp_queue.empty() &&
      _write_resp_queue.front().delay == 0) {
    _write_resp_queue.pop();
  }
}

uint8_t AXILiteMemory::b_resp() const noexcept {
  return _write_resp_queue.empty() ? 0 : _write_resp_queue.front().resp;
}

// AR
void AXILiteMemory::ar_valid(addr_t addr) {
  _read_queue.push({addr, 0, false, read_delay_});
}

bool AXILiteMemory::ar_ready() const noexcept { return true; }

// R
bool AXILiteMemory::r_valid() const noexcept {
  return !_read_queue.empty() && _read_queue.front().processed;
}

void AXILiteMemory::r_ready(bool ready) {
  if (ready && !_read_queue.empty() && _read_queue.front().processed) {
    _read_queue.pop();
  }
}

word_t AXILiteMemory::r_data() const noexcept {
  return _read_queue.empty() ? 0 : _read_queue.front().data;
}

uint8_t AXILiteMemory::r_resp() const noexcept {
  return 0; // OKAY
}

} // namespace demu::hal::axi
