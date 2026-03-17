#pragma once

#include "../isa/isa.hh"

namespace demu::hal {
using namespace isa;

class Hardware {
public:
  virtual ~Hardware() = default;

  virtual void clock_tick() = 0;
  virtual void reset() = 0;

  virtual const char *name() const noexcept { return "Unknown Hardware"; }
};

template <typename DUT> class RTLHardware : public Hardware {
public:
  RTLHardware() : dut_(std::make_unique<DUT>()) { dut_->eval(); }
  virtual ~RTLHardware() = default;

protected:
  std::unique_ptr<DUT> dut_;
};

} // namespace demu::hal
