#pragma once

#include "../../device.hh"

namespace demu::hal::axi {

using namespace isa;

class AXILiteSlave : public Device {
public:
  explicit AXILiteSlave(const risc::DeviceDescriptor &desc) : Device(desc) {}

  AXILiteSlave(std::string name, risc::DeviceType type, addr_t base,
               size_t size)
      : Device(std::move(name), type, base, size) {}

  ~AXILiteSlave() override = default;

  // AW
  virtual void aw_valid(addr_t addr) = 0;
  virtual bool aw_ready() const noexcept = 0;
  // W
  virtual void w_valid(word_t data, byte_t strb) = 0;
  virtual bool w_ready() const noexcept = 0;
  // B
  virtual bool b_valid() const noexcept = 0;
  virtual void b_ready(bool ready) = 0;
  virtual uint8_t b_resp() const noexcept = 0;
  // AR
  virtual void ar_valid(addr_t addr) = 0;
  virtual bool ar_ready() const noexcept = 0;
  // R
  virtual bool r_valid() const noexcept = 0;
  virtual void r_ready(bool ready) = 0;
  virtual word_t r_data() const noexcept = 0;
  virtual uint8_t r_resp() const noexcept = 0;
};

} // namespace demu::hal::axi
