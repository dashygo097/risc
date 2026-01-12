#include "demu/hal/memory.hh"
#include <cstring>
#include <fstream>
#include <iomanip>
#include <iostream>

namespace demu::hal {
Memory::Memory(size_t size, addr_t base_addr)
    : _memory(size, 0), _base_addr(base_addr) {}

word_t Memory::read_word(addr_t addr) const noexcept {
  if (!is_valid_addr(addr))
    return 0x0;

  word_t read_data = 0;
  addr_t offset = get_offset(addr);

  for (size_t i = 0; i < sizeof(word_t) / sizeof(byte_t); i++) {
    read_data |= static_cast<word_t>(_memory[offset + i]) << (i * 8);
  }
  return read_data;
}

half_t Memory::read_half(addr_t addr) const noexcept {
  if (!is_valid_addr(addr))
    return 0x0;

  half_t read_data = 0;
  addr_t offset = get_offset(addr);

  for (size_t i = 0; i < sizeof(half_t) / sizeof(byte_t); i++) {
    read_data |= static_cast<word_t>(_memory[offset + i]) << (i * 8);
  }
  return read_data;
}

byte_t Memory::read_byte(addr_t addr) const noexcept {
  if (!is_valid_addr(addr))
    return 0x0;

  addr_t offset = get_offset(addr);

  return _memory[offset];
}

void Memory::write_word(addr_t addr, word_t data) {
  if (!is_valid_addr(addr))
    return;

  addr_t offset = get_offset(addr);
  for (size_t i = 0; i < sizeof(word_t) / sizeof(byte_t); i++) {
    _memory[offset + i] = (data >> (i * 8)) & 0xFF;
  }
}

void Memory::write_half(addr_t addr, half_t data) {
  if (!is_valid_addr(addr))
    return;

  addr_t offset = get_offset(addr);
  for (size_t i = 0; i < sizeof(half_t) / sizeof(byte_t); i++) {
    _memory[offset + i] = (data >> (i * 8)) & 0xFF;
  }
}

void Memory::write_byte(addr_t addr, byte_t data) {
  if (!is_valid_addr(addr))
    return;

  addr_t offset = get_offset(addr);
  _memory[offset] = data;
}

bool Memory::load_binary(const std::string &filename, addr_t offset) {
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

void Memory::clear() { std::fill(_memory.begin(), _memory.end(), 0); }

void Memory::dump(addr_t start, addr_t length) const {
  for (addr_t addr = start; addr < start + length; addr += 16) {
    std::cout << std::hex << std::setw(8) << std::setfill('0') << addr << ": ";

    for (size_t i = 0; i < 16 && addr + i < start + length; i++) {
      if (i == 8)
        std::cout << " ";
      std::cout << std::hex << std::setw(2) << std::setfill('0')
                << (int_t)read_byte(addr + i) << " ";
    }

    std::cout << " |";
    for (size_t i = 0; i < 16 && addr + i < start + length; i++) {
      byte_t c = read_byte(addr + i);
      std::cout << (c >= 32 && c < 127 ? (char)c : '.');
    }
    std::cout << "|" << std::endl;
  }
  std::cout << std::dec << std::endl;
}

bool Memory::is_valid_addr(addr_t addr) const noexcept {
  addr_t offset = addr - _base_addr;
  return offset < _memory.size();
}

addr_t Memory::get_offset(addr_t addr) const noexcept {
  return addr - _base_addr;
}

} // namespace demu::hal
