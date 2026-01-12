#pragma once

#include "./ihardware.hh"

namespace demu::hal {

template <typename DUT> class RTLHardware : public IHardware {
public:
  RTLHardware() : _dut(std::make_unique<DUT>()) { _dut->eval(); }

  virtual ~RTLHardware() = default;

  void reset() override {
    _dut->reset = 1;
    _dut->clock = 0;
    _dut->eval();
    _dut->clock = 1;
    _dut->eval();
    _dut->reset = 0;
    _dut->eval();
  }

  void clock_tick() override {
    _dut->clock = 0;
    _dut->eval();
    _dut->clock = 1;
    _dut->eval();
  }

protected:
  std::unique_ptr<DUT> _dut;
};

} // namespace demu::hal
