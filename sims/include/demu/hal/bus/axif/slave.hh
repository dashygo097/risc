#pragma once
#include "../../device.hh"

namespace demu::hal::axi {
using namespace isa;

class AXIFullSlave : public Device {
public:
  explicit AXIFullSlave(const risc::DeviceDescriptor &desc) : Device(desc) {}
  ~AXIFullSlave() override = default;

  // AW Channel
  virtual void aw_valid(bool valid, uint32_t id, addr_t addr, uint8_t len,
                        uint8_t size, uint8_t burst) = 0;
  virtual auto aw_ready() const noexcept -> bool = 0;

  // W Channel
  virtual void w_valid(bool valid, word_t data, byte_t strb, bool last) = 0;
  virtual auto w_ready() const noexcept -> bool = 0;

  // B Channel
  virtual auto b_valid() const noexcept -> bool = 0;
  virtual void b_ready(bool ready) = 0;
  virtual auto b_resp() const noexcept -> uint8_t = 0;
  virtual auto b_id() const noexcept -> uint32_t = 0;

  // AR Channel
  virtual void ar_valid(bool valid, uint32_t id, addr_t addr, uint8_t len,
                        uint8_t size, uint8_t burst) = 0;
  virtual auto ar_ready() const noexcept -> bool = 0;

  // R Channel
  virtual auto r_valid() const noexcept -> bool = 0;
  virtual void r_ready(bool ready) = 0;
  virtual auto r_data() const noexcept -> word_t = 0;
  virtual auto r_resp() const noexcept -> uint8_t = 0;
  virtual auto r_id() const noexcept -> uint32_t = 0;
  virtual auto r_last() const noexcept -> bool = 0;
};

} // namespace demu::hal::axi
