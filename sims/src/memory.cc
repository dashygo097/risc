#include "rvsim/memory.hh"
#include <cstring>
#include <fstream>
#include <iomanip>
#include <iostream>

Memory::Memory(size_t size, uint32_t base_addr)
    : _memory(size, 0), _base_addr(base_addr) {}

uint32_t Memory::read32(uint32_t addr) const noexcept {
  if (!is_valid_addr(addr))
    return 0;
  uint32_t offset = translate_addr(addr);
  return (_memory[offset + 0] << 0) | (_memory[offset + 1] << 8) |
         (_memory[offset + 2] << 16) | (_memory[offset + 3] << 24);
}

uint16_t Memory::read16(uint32_t addr) const noexcept {
  if (!is_valid_addr(addr))
    return 0;
  uint32_t offset = translate_addr(addr);
  return (_memory[offset + 0] << 0) | (_memory[offset + 1] << 8);
}

uint8_t Memory::read8(uint32_t addr) const noexcept {
  if (!is_valid_addr(addr))
    return 0;
  uint32_t offset = translate_addr(addr);
  return _memory[offset];
}

void Memory::write32(uint32_t addr, uint32_t data) {
  if (!is_valid_addr(addr))
    return;
  uint32_t offset = translate_addr(addr);
  _memory[offset + 0] = (data >> 0) & 0xFF;
  _memory[offset + 1] = (data >> 8) & 0xFF;
  _memory[offset + 2] = (data >> 16) & 0xFF;
  _memory[offset + 3] = (data >> 24) & 0xFF;
}

void Memory::write16(uint32_t addr, uint16_t data) {
  if (!is_valid_addr(addr))
    return;
  uint32_t offset = translate_addr(addr);
  _memory[offset + 0] = (data >> 0) & 0xFF;
  _memory[offset + 1] = (data >> 8) & 0xFF;
}

void Memory::write8(uint32_t addr, uint8_t data) {
  if (!is_valid_addr(addr))
    return;
  uint32_t offset = translate_addr(addr);
  _memory[offset] = data;
}

bool Memory::load_binary(const std::string &filename, uint32_t offset) {
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
      _memory[offset + i] = static_cast<uint8_t>(buffer[i]);
    }
    return true;
  }

  return false;
}

void Memory::clear() { std::fill(_memory.begin(), _memory.end(), 0); }

void Memory::dump(uint32_t start, uint32_t length) const {
  for (uint32_t addr = start; addr < start + length; addr += 16) {
    std::cout << std::hex << std::setw(8) << std::setfill('0') << addr << ": ";

    for (uint32_t i = 0; i < 16 && addr + i < start + length; i++) {
      if (i == 8)
        std::cout << " ";
      std::cout << std::hex << std::setw(2) << std::setfill('0')
                << (int)read8(addr + i) << " ";
    }

    std::cout << " |";
    for (uint32_t i = 0; i < 16 && addr + i < start + length; i++) {
      uint8_t c = read8(addr + i);
      std::cout << (c >= 32 && c < 127 ? (char)c : '.');
    }
    std::cout << "|" << std::endl;
  }
  std::cout << std::dec << std::endl;
}

bool Memory::is_valid_addr(uint32_t addr) const noexcept {
  uint32_t offset = addr - _base_addr;
  return offset < _memory.size();
}

uint32_t Memory::translate_addr(uint32_t addr) const noexcept {
  return addr - _base_addr;
}
