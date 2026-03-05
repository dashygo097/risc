#pragma once
#include "./emu.hh"

namespace demu::hal {

class PortHandler {
public:
  virtual ~PortHandler() = default;

  virtual void handle(EmulatedHardware *slave) noexcept = 0;

  virtual const char *protocol_name() const noexcept = 0;
};

} // namespace demu::hal
