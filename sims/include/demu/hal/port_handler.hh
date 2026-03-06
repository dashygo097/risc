#pragma once
#include "./hardware.hh"

namespace demu::hal {

class PortHandler {
public:
  virtual ~PortHandler() = default;

  virtual void handle(Hardware *hw) noexcept = 0;

  virtual const char *protocol_name() const noexcept = 0;
};

} // namespace demu::hal
