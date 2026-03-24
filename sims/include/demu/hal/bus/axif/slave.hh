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
  virtual bool aw_ready() const noexcept = 0;

  // W Channel
  virtual void w_valid(bool valid, word_t data, byte_t strb, bool last) = 0;
  virtual bool w_ready() const noexcept = 0;

  // B Channel
  virtual bool b_valid() const noexcept = 0;
  virtual void b_ready(bool ready) = 0;
  virtual uint8_t b_resp() const noexcept = 0;
  virtual uint32_t b_id() const noexcept = 0;

  // AR Channel
  virtual void ar_valid(bool valid, uint32_t id, addr_t addr, uint8_t len,
                        uint8_t size, uint8_t burst) = 0;
  virtual bool ar_ready() const noexcept = 0;

  // R Channel
  virtual bool r_valid() const noexcept = 0;
  virtual void r_ready(bool ready) = 0;
  virtual word_t r_data() const noexcept = 0;
  virtual uint8_t r_resp() const noexcept = 0;
  virtual uint32_t r_id() const noexcept = 0;
  virtual bool r_last() const noexcept = 0;
};

} // namespace demu::hal::axi
