#include "demu/hal/axi/memory.hh"
#include <algorithm>
#include <fstream>
#include <iostream>

namespace demu::hal {

AXIMemory::AXIMemory(size_t size, addr_t base_addr, size_t read_delay,
                     size_t write_delay)
    : memory_(size, 0), base_addr_(base_addr), addr_range_(size),
      read_delay_cycles_(read_delay), write_delay_cycles_(write_delay) {
  reset();
}

void AXIMemory::reset() {
  std::fill(memory_.begin(), memory_.end(), 0);

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

    const addr_t offset = addr - base_addr_;
    if (offset < memory_.size() && offset + 3 < memory_.size()) {
      for (int i = 0; i < 4; ++i) {
        if (wdata.strb & (1 << i)) {
          memory_[offset + i] =
              static_cast<byte_t>((wdata.data >> (i * 8)) & 0xFF);
        }
      }
    }

    write_resp_queue_.push({0, write_delay_cycles_}); // OKAY response
  }
}

void AXIMemory::process_reads() {
  if (!read_queue_.empty() && !read_queue_.front().processed) {
    ReadTransaction &read_trans = read_queue_.front();

    if (read_trans.delay > 0) {
      read_trans.delay--;
    } else {
      const addr_t offset = read_trans.addr - base_addr_;
      word_t data = 0;

      if (offset < memory_.size() && offset + 3 < memory_.size()) {
        data = static_cast<word_t>(memory_[offset]) |
               (static_cast<word_t>(memory_[offset + 1]) << 8) |
               (static_cast<word_t>(memory_[offset + 2]) << 16) |
               (static_cast<word_t>(memory_[offset + 3]) << 24);
      }

      read_trans.data = data;
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

bool AXIMemory::load_binary(const std::string &filename, addr_t offset) {
  std::ifstream file(filename, std::ios::binary | std::ios::ate);
  if (!file.is_open()) {
    std::cerr << "Failed to open binary file: " << filename << '\n';
    return false;
  }

  const std::streamsize size = file.tellg();
  file.seekg(0, std::ios::beg);

  if (offset + size > memory_.size()) {
    std::cerr << "Binary file too large for memory\n";
    return false;
  }

  std::vector<char> buffer(static_cast<size_t>(size));
  if (!file.read(buffer.data(), size)) {
    return false;
  }

  for (size_t i = 0; i < buffer.size(); ++i) {
    memory_[offset + i] = static_cast<byte_t>(buffer[i]);
  }

  return true;
}

} // namespace demu::hal
