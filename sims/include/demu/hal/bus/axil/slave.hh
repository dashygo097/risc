#pragma once

#include "../../device.hh"

namespace demu::hal::axil {

using namespace isa;

class AXILiteSlave : public Device {
public:
  explicit AXILiteSlave(const risc::DeviceDescriptor &desc) : Device(desc) {}

  ~AXILiteSlave() override = default;

  // AW
  virtual void aw_valid(addr_t addr) = 0;
  virtual auto aw_ready() const noexcept -> bool = 0;
  // W
  virtual void w_valid(word_t data, byte_t strb) = 0;
  virtual auto w_ready() const noexcept -> bool = 0;
  // B
  virtual auto b_valid() const noexcept -> bool = 0;
  virtual void b_ready(bool ready) = 0;
  virtual auto b_resp() const noexcept -> uint8_t = 0;
  // AR
  virtual void ar_valid(addr_t addr) = 0;
  virtual auto ar_ready() const noexcept -> bool = 0;
  // R
  virtual auto r_valid() const noexcept -> bool = 0;
  virtual void r_ready(bool ready) = 0;
  virtual auto r_data() const noexcept -> word_t = 0;
  virtual auto r_resp() const noexcept -> uint8_t = 0;
};

} // namespace demu::hal::axil
