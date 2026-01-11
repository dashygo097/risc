#pragma once

#include "./signals.hh"
#include "./slave.hh"
#include <fstream>
#include <queue>

namespace demu::hal {

class AXIMemory : public AXISlave {
public:
  AXIMemory(size_t size, addr_t base_addr = 0x0, size_t read_delay = 0,
            size_t write_delay = 0)
      : _memory(size, 0), _base_addr(base_addr), _addr_range(size),
        _read_delay_cycles(read_delay), _write_delay_cycles(write_delay) {
    reset();
  }

  const char *name() const noexcept override { return "AXI RAM"; }

  addr_t base_address() const noexcept override { return _base_addr; }
  size_t address_range() const noexcept override { return _addr_range; }

  void set_read_delay(size_t cycles) { _read_delay_cycles = cycles; }
  void set_write_delay(size_t cycles) { _write_delay_cycles = cycles; }

  void reset() override {
    std::fill(_memory.begin(), _memory.end(), 0);
    while (!_write_addr_queue.empty())
      _write_addr_queue.pop();
    while (!_write_data_queue.empty())
      _write_data_queue.pop();
    while (!_write_resp_queue.empty())
      _write_resp_queue.pop();
    while (!_read_queue.empty())
      _read_queue.pop();
  }

  void clock_tick() override {
    // Process write transactions when both address and data are available
    if (!_write_addr_queue.empty() && !_write_data_queue.empty()) {
      addr_t addr = _write_addr_queue.front();
      word_t data = _write_data_queue.front().data;
      byte_t strb = _write_data_queue.front().strb;

      _write_addr_queue.pop();
      _write_data_queue.pop();

      // Perform write
      addr_t offset = addr - _base_addr;
      if (offset < _memory.size() && offset + 3 < _memory.size()) {
        for (int i = 0; i < 4; i++) {
          if (strb & (1 << i)) {
            _memory[offset + i] = (data >> (i * 8)) & 0xFF;
          }
        }
      }

      // Queue write response with delay
      _write_resp_queue.push({0, _write_delay_cycles}); // OKAY response
    }

    // Decrement write response delay counters
    if (!_write_resp_queue.empty() && _write_resp_queue.front().delay > 0) {
      _write_resp_queue.front().delay--;
    }

    // Process read transactions
    if (!_read_queue.empty() && !_read_queue.front().processed) {
      auto &read_trans = _read_queue.front();

      if (read_trans.delay > 0) {
        read_trans.delay--;
      } else {
        addr_t offset = read_trans.addr - _base_addr;
        word_t data = 0;

        if (offset < _memory.size() && offset + 3 < _memory.size()) {
          data = static_cast<word_t>(_memory[offset]) |
                 (static_cast<word_t>(_memory[offset + 1]) << 8) |
                 (static_cast<word_t>(_memory[offset + 2]) << 16) |
                 (static_cast<word_t>(_memory[offset + 3]) << 24);
        }

        read_trans.data = data;
        read_trans.processed = true;
      }
    }
  }

  // Write Address Channel - always ready
  void aw_valid(addr_t addr) override { _write_addr_queue.push(addr); }

  bool aw_ready() const noexcept override {
    return true; // Always ready
  }

  // Write Data Channel - always ready
  void w_valid(word_t data, byte_t strb) override {
    _write_data_queue.push({data, strb});
  }

  bool w_ready() const noexcept override {
    return true; // Always ready
  }

  // Write Response Channel
  bool b_valid() const noexcept override {
    return !_write_resp_queue.empty() && _write_resp_queue.front().delay == 0;
  }

  void b_ready(bool ready) override {
    if (ready && !_write_resp_queue.empty() &&
        _write_resp_queue.front().delay == 0) {
      _write_resp_queue.pop();
    }
  }

  uint8_t b_resp() const noexcept override {
    return _write_resp_queue.empty() ? 0 : _write_resp_queue.front().resp;
  }

  // Read Address Channel - always ready
  void ar_valid(addr_t addr) override {
    _read_queue.push({addr, 0, false, _read_delay_cycles});
  }

  bool ar_ready() const noexcept override {
    return true; // Always ready
  }

  // Read Data Channel
  bool r_valid() const noexcept override {
    return !_read_queue.empty() && _read_queue.front().processed;
  }

  void r_ready(bool ready) override {
    if (ready && !_read_queue.empty() && _read_queue.front().processed) {
      _read_queue.pop();
    }
  }

  word_t r_data() const noexcept override {
    return _read_queue.empty() ? 0 : _read_queue.front().data;
  }

  uint8_t r_resp() const noexcept override {
    return 0; // OKAY
  }

  word_t read_word(addr_t addr) const noexcept {
    addr_t offset = addr - _base_addr;
    if (offset >= _memory.size() || offset + 3 >= _memory.size())
      return 0;
    return static_cast<word_t>(_memory[offset]) |
           (static_cast<word_t>(_memory[offset + 1]) << 8) |
           (static_cast<word_t>(_memory[offset + 2]) << 16) |
           (static_cast<word_t>(_memory[offset + 3]) << 24);
  }

  void write_word(addr_t addr, word_t data) {
    addr_t offset = addr - _base_addr;
    if (offset >= _memory.size() || offset + 3 >= _memory.size())
      return;
    _memory[offset] = data & 0xFF;
    _memory[offset + 1] = (data >> 8) & 0xFF;
    _memory[offset + 2] = (data >> 16) & 0xFF;
    _memory[offset + 3] = (data >> 24) & 0xFF;
  }

  bool load_binary(const std::string &filename, addr_t offset = 0) {
    std::ifstream file(filename, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
      std::cerr << "Failed to open binary file: " << filename << std::endl;
      return false;
    }

    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);

    if (offset + size > _memory.size()) {
      std::cerr << "Binary file too large for memory" << std::endl;
      return false;
    }

    std::vector<char> buffer(size);

    if (file.read(buffer.data(), size)) {
      for (size_t i = 0; i < buffer.size(); i++) {
        _memory[offset + i] = static_cast<byte_t>(buffer[i]);
      }
      return true;
    }

    return false;
  }

  byte_t *get_ptr(addr_t offset = 0) {
    if (offset >= _memory.size())
      return nullptr;
    return _memory.data() + offset;
  }

  const byte_t *get_ptr(addr_t offset = 0) const {
    if (offset >= _memory.size())
      return nullptr;
    return _memory.data() + offset;
  }

private:
  std::vector<byte_t> _memory;
  addr_t _base_addr;
  size_t _addr_range;
  size_t _read_delay_cycles;
  size_t _write_delay_cycles;

  struct WriteData {
    word_t data;
    byte_t strb;
  };

  struct WriteResponse {
    uint8_t resp;
    size_t delay;
  };

  struct ReadTransaction {
    addr_t addr;
    word_t data;
    bool processed;
    size_t delay;
  };

  // Queues for pipelined transactions
  std::queue<addr_t> _write_addr_queue;
  std::queue<WriteData> _write_data_queue;
  std::queue<WriteResponse> _write_resp_queue;
  std::queue<ReadTransaction> _read_queue;
};

} // namespace demu::hal
