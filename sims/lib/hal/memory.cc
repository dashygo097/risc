#include "demu/hal/memory.hh"
#include <cstring>
#include <fstream>
#include <iomanip>
#include <sstream>

namespace demu::hal {

Memory::Memory(size_t size, addr_t base_addr)
    : _memory(size, 0), _base_addr(base_addr) {
  HAL_INFO("Memory initialized: Size={} bytes, BaseAddr=0x{:08x}", size,
           base_addr);
}

word_t Memory::read_word(addr_t addr) const noexcept {
  if (!is_valid_addr(addr)) {
    HAL_WARN("Invalid Word Read at 0x{:08x}", addr);
    return 0x0;
  }

  word_t read_data = 0;
  addr_t offset = to_offset(addr);

  for (size_t i = 0; i < sizeof(word_t) / sizeof(byte_t); i++) {
    read_data |= static_cast<word_t>(_memory[offset + i]) << (i * 8);
  }

  return read_data;
}

half_t Memory::read_half(addr_t addr) const noexcept {
  if (!is_valid_addr(addr)) {
    HAL_WARN("Invalid Half Read at 0x{:08x}", addr);
    return 0x0;
  }

  half_t read_data = 0;
  addr_t offset = to_offset(addr);

  for (size_t i = 0; i < sizeof(half_t) / sizeof(byte_t); i++) {
    read_data |= static_cast<word_t>(_memory[offset + i]) << (i * 8);
  }

  return read_data;
}

byte_t Memory::read_byte(addr_t addr) const noexcept {
  if (!is_valid_addr(addr)) {
    HAL_WARN("Invalid Byte Read at 0x{:08x}", addr);
    return 0x0;
  }

  addr_t offset = to_offset(addr);
  byte_t data = _memory[offset];

  return data;
}

void Memory::write_word(addr_t addr, word_t data) {
  if (!is_valid_addr(addr)) {
    HAL_WARN("Invalid Word Write at 0x{:08x}", addr);
    return;
  }

  addr_t offset = to_offset(addr);
  for (size_t i = 0; i < sizeof(word_t) / sizeof(byte_t); i++) {
    _memory[offset + i] = (data >> (i * 8)) & 0xFF;
  }
}

void Memory::write_half(addr_t addr, half_t data) {
  if (!is_valid_addr(addr)) {
    HAL_WARN("Invalid Half Write at 0x{:08x}", addr);
    return;
  }

  addr_t offset = to_offset(addr);
  for (size_t i = 0; i < sizeof(half_t) / sizeof(byte_t); i++) {
    _memory[offset + i] = (data >> (i * 8)) & 0xFF;
  }
}

void Memory::write_byte(addr_t addr, byte_t data) {
  if (!is_valid_addr(addr)) {
    HAL_WARN("Invalid Byte Write at 0x{:08x}", addr);
    return;
  }

  addr_t offset = to_offset(addr);
  _memory[offset] = data;
}

bool Memory::load_binary(const std::string &filename, addr_t offset) {
  std::ifstream file(filename, std::ios::binary | std::ios::ate);
  if (!file.is_open()) {
    HAL_ERROR("Failed to open binary file: {}", filename);
    return false;
  }

  std::streamsize size = file.tellg();
  file.seekg(0, std::ios::beg);

  if (offset + size > _memory.size()) {
    HAL_ERROR("Binary file ({}) too large for memory (Size: {}, Available: {})",
              filename, size, _memory.size() - offset);
    return false;
  }

  std::vector<char> buffer(size);
  if (file.read(buffer.data(), size)) {
    for (size_t i = 0; i < (size_t)buffer.size(); i++) {
      _memory[offset + i] = static_cast<byte_t>(buffer[i]);
    }
    HAL_INFO("Successfully loaded binary '{}' ({} bytes) at offset 0x{:08x}",
             filename, size, offset);
    return true;
  }

  HAL_ERROR("Read error while loading binary: {}", filename);
  return false;
}

void Memory::clear() {
  HAL_DEBUG("Memory cleared (zeroed)");
  std::fill(_memory.begin(), _memory.end(), 0);
}

void Memory::dump(addr_t start, addr_t length) const {
  HAL_INFO("Memory Dump [0x{:08x} - 0x{:08x}]:", start, start + length);
  for (addr_t addr = start; addr < start + length; addr += 16) {
    std::stringstream ss;
    ss << std::hex << std::setw(8) << std::setfill('0') << addr << ": ";

    for (size_t i = 0; i < 16 && addr + i < start + length; i++) {
      if (i == 8)
        ss << " ";
      ss << std::hex << std::setw(2) << std::setfill('0')
         << (int)read_byte(addr + i) << " ";
    }

    ss << " |";
    for (size_t i = 0; i < 16 && addr + i < start + length; i++) {
      byte_t c = read_byte(addr + i);
      ss << (c >= 32 && c < 127 ? (char)c : '.');
    }
    ss << "|";

    HAL_INFO("{}", ss.str());
  }
}

bool Memory::is_valid_addr(addr_t addr) const noexcept {
  addr_t offset = addr - _base_addr;
  bool valid = offset < _memory.size();
  return valid;
}

addr_t Memory::to_offset(addr_t addr) const noexcept {
  return addr - _base_addr;
}

} // namespace demu::hal
