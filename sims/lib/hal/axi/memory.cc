#include "demu/hal/axi/memory.hh"
#include <algorithm>
#include <fstream>
#include <iostream>

namespace demu::hal {

AXIMemory::AXIMemory(size_t size, addr_t base_addr, size_t read_delay,
                     size_t write_delay)
    : _memory(std::make_unique<Memory>(size, base_addr)),
      read_delay_cycles_(read_delay), write_delay_cycles_(write_delay) {
  reset();
}

void AXIMemory::reset() {
  _memory->clear();

  // Clear all queues
  write_addr_queue_ = {};
  write_data_queue_ = {};
  write_resp_queue_ = {};
  read_queue_ = {};
}

void AXIMemory::clock_tick() {
  process_writes();
  update_delays();
  process_reads();
}

void AXIMemory::process_writes() {
  if (!write_addr_queue_.empty() && !write_data_queue_.empty()) {
    const addr_t addr = write_addr_queue_.front();
    const WriteData &wdata = write_data_queue_.front();

    write_addr_queue_.pop();
    write_data_queue_.pop();

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
    write_resp_queue_.push({resp, write_delay_cycles_});
  }
}

void AXIMemory::process_reads() {
  if (!read_queue_.empty() && !read_queue_.front().processed) {
    ReadTransaction &read_trans = read_queue_.front();

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
  if (!write_resp_queue_.empty() && write_resp_queue_.front().delay > 0) {
    write_resp_queue_.front().delay--;
  }
}

// AW
void AXIMemory::aw_valid(addr_t addr) { write_addr_queue_.push(addr); }

bool AXIMemory::aw_ready() const noexcept { return true; }

// W
void AXIMemory::w_valid(word_t data, byte_t strb) {
  write_data_queue_.push({data, strb});
}

bool AXIMemory::w_ready() const noexcept { return true; }

// B
bool AXIMemory::b_valid() const noexcept {
  return !write_resp_queue_.empty() && write_resp_queue_.front().delay == 0;
}

void AXIMemory::b_ready(bool ready) {
  if (ready && !write_resp_queue_.empty() &&
      write_resp_queue_.front().delay == 0) {
    write_resp_queue_.pop();
  }
}

uint8_t AXIMemory::b_resp() const noexcept {
  return write_resp_queue_.empty() ? 0 : write_resp_queue_.front().resp;
}

// AR
void AXIMemory::ar_valid(addr_t addr) {
  read_queue_.push({addr, 0, false, read_delay_cycles_});
}

bool AXIMemory::ar_ready() const noexcept { return true; }

// R
bool AXIMemory::r_valid() const noexcept {
  return !read_queue_.empty() && read_queue_.front().processed;
}

void AXIMemory::r_ready(bool ready) {
  if (ready && !read_queue_.empty() && read_queue_.front().processed) {
    read_queue_.pop();
  }
}

word_t AXIMemory::r_data() const noexcept {
  return read_queue_.empty() ? 0 : read_queue_.front().data;
}

uint8_t AXIMemory::r_resp() const noexcept {
  return 0; // OKAY
}

} // namespace demu::hal
