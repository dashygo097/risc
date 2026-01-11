#pragma once

#include "./signals.hh"
#include "./slave.hh"
#include <fstream>

namespace demu::hal {

class AXIMemory : public AXISlave {
public:
  AXIMemory(size_t size, addr_t base_addr = 0x0)
      : _memory(size, 0), _base_addr(base_addr), _addr_range(size) {
    reset();
  }

  const char *name() const noexcept override { return "AXI RAM"; }

  addr_t base_address() const noexcept override { return _base_addr; }
  size_t address_range() const noexcept override { return _addr_range; }

  void reset() override {
    std::fill(_memory.begin(), _memory.end(), 0);
    _write_trans = {};
    _read_trans = {};
    _aw_ready = true;
    _w_ready = false;
    _b_valid = false;
    _ar_ready = true;
    _r_valid = false;
  }

  void clock_tick() override {
    if (_write_trans.addr_valid && _write_trans.data_valid && !_b_valid) {
      addr_t offset = _write_trans.addr - _base_addr;
      if (offset + 3 < _memory.size()) {
        for (int i = 0; i < 4; i++) {
          if (_write_trans.strb & (1 << i)) {
            _memory[offset + i] = (_write_trans.data >> (i * 8)) & 0xFF;
          }
        }
      }
      _b_valid = true;
      _write_trans.addr_valid = false;
      _write_trans.data_valid = false;
      _aw_ready = true;
      _w_ready = false;
    }

    if (_b_valid && _write_trans.resp_ready) {
      _b_valid = false;
      _aw_ready = true;
    }

    if (_read_trans.active && !_r_valid) {
      addr_t offset = _read_trans.addr - _base_addr;
      word_t data = 0;
      if (offset + 3 < _memory.size()) {
        data = static_cast<word_t>(_memory[offset]) |
               (static_cast<word_t>(_memory[offset + 1]) << 8) |
               (static_cast<word_t>(_memory[offset + 2]) << 16) |
               (static_cast<word_t>(_memory[offset + 3]) << 24);
      }
      _read_trans.data = data;
      _r_valid = true;
      _read_trans.active = false;
      _ar_ready = true;
    }

    if (_r_valid && _read_trans.valid) {
      _r_valid = false;
      _ar_ready = true;
    }
  }

  void aw_valid(addr_t addr) override {
    if (_aw_ready) {
      _write_trans.addr = addr;
      _write_trans.addr_valid = true;
      _aw_ready = false;
      _w_ready = true;
    }
  }

  bool aw_ready() const noexcept override { return _aw_ready; }

  void w_valid(word_t data, byte_t strb) override {
    if (_w_ready) {
      _write_trans.data = data;
      _write_trans.strb = strb;
      _write_trans.data_valid = true;
      _w_ready = false;
    }
  }

  bool w_ready() const noexcept override { return _w_ready; }

  bool b_valid() const noexcept override { return _b_valid; }
  void b_ready(bool ready) override { _write_trans.resp_ready = ready; }
  uint8_t b_resp() const noexcept override { return 0; } // OKAY

  void ar_valid(addr_t addr) override {
    if (_ar_ready) {
      _read_trans.addr = addr;
      _read_trans.active = true;
      _ar_ready = false;
    }
  }

  bool ar_ready() const noexcept override { return _ar_ready; }

  bool r_valid() const noexcept override { return _r_valid; }
  void r_ready(bool ready) override { _read_trans.valid = ready; }
  word_t r_data() const noexcept override { return _read_trans.data; }
  uint8_t r_resp() const noexcept override { return 0; } // OKAY

  word_t read_word(addr_t addr) const noexcept {
    addr_t offset = addr - _base_addr;
    if (offset + 3 >= _memory.size())
      return 0;
    return static_cast<word_t>(_memory[offset]) |
           (static_cast<word_t>(_memory[offset + 1]) << 8) |
           (static_cast<word_t>(_memory[offset + 2]) << 16) |
           (static_cast<word_t>(_memory[offset + 3]) << 24);
  }

  void write_word(addr_t addr, word_t data) {
    addr_t offset = addr - _base_addr;
    if (offset + 3 >= _memory.size())
      return;
    _memory[offset] = data & 0xFF;
    _memory[offset + 1] = (data >> 8) & 0xFF;
    _memory[offset + 2] = (data >> 16) & 0xFF;
    _memory[offset + 3] = (data >> 24) & 0xFF;
  }

  bool load_binary(const std::string &filename, addr_t offset = 0) {
    std::ifstream file(filename, std::ios::binary | std::ios::ate);
    if (!file.is_open())
      return false;

    std::streamsize file_size = file.tellg();
    file.seekg(0, std::ios::beg);

    if (offset + file_size > _memory.size())
      return false;

    file.read(reinterpret_cast<char *>(_memory.data() + offset), file_size);
    return file.good();
  }

  byte_t *get_ptr(addr_t offset = 0) {
    if (offset >= _memory.size())
      return nullptr;
    return _memory.data() + offset;
  }

private:
  std::vector<byte_t> _memory;
  addr_t _base_addr;
  size_t _addr_range;

  // AXI Protocol State
  AXIWriteTransaction _write_trans;
  AXIReadTransaction _read_trans;

  bool _aw_ready;
  bool _w_ready;
  bool _b_valid;
  bool _ar_ready;
  bool _r_valid;
};

} // namespace demu::hal
