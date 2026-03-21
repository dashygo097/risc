#pragma once

#include "./allocator.hh"
#include "./hardware.hh"
#include "risc.pb.h"

namespace demu::hal {
using namespace isa;

class Device : public Hardware {
public:
  explicit Device(const risc::DeviceDescriptor &desc) : desc_(desc) {}

  virtual ~Device() = default;

  [[nodiscard]] addr_t base_address() const noexcept {
    return static_cast<addr_t>(desc_.base());
  }
  [[nodiscard]] size_t address_range() const noexcept {
    return static_cast<size_t>(desc_.size());
  }
  [[nodiscard]] const char *name() const noexcept override {
    return desc_.name().c_str();
  }
  [[nodiscard]] risc::DeviceType device_type() const noexcept {
    return desc_.type();
  }
  [[nodiscard]] const risc::DeviceDescriptor &descriptor() const noexcept {
    return desc_;
  }

  [[nodiscard]] bool owns_address(addr_t addr) const noexcept {
    const addr_t base = base_address();
    return addr >= base && addr < (base + address_range());
  }
  [[nodiscard]] bool is_valid_offset(addr_t offset) const noexcept {
    return offset < address_range();
  }
  [[nodiscard]] addr_t to_offset(addr_t addr) const noexcept {
    return addr - base_address();
  }

  // NOTE: Every derived device should have a memory allocator which takes up
  // some address spaces
  [[nodiscard]] virtual MemoryAllocator *allocator() const noexcept {
    return nullptr;
  }
  virtual void dump(addr_t start, size_t size) const noexcept {}

  bool load_binary(const std::string &filename, addr_t offset = 0) {
    auto *alloc = allocator();
    if (!alloc)
      return false;
    return alloc->load_binary(filename, offset);
  }

private:
  risc::DeviceDescriptor desc_;
};

} // namespace demu::hal
