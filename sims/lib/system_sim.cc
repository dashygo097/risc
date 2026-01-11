#ifdef ENABLE_SYSTEM
#include "demu/system_sim.hh"
#include "demu/elf_loader.hh"
#include <iomanip>
#include <iostream>

namespace demu {
SystemSimulator::SystemSimulator(bool enable_trace)
    : _dut(new system_t), _trace(new ExecutionTrace()), _time_counter(0),
      _timeout(1000000), _terminate(false), _verbose(false),
      _trace_enabled(enable_trace) {
#ifdef ENABLE_TRACE
  if (_trace_enabled) {
    Verilated::traceEverOn(true);
    _vcd = std::make_unique<VerilatedVcdC>();
    _dut->trace(_vcd.get(), 99);
    _vcd->open(strcpy(ISA_NAME, "_system.vcd"));
  }
#endif
}

SystemSimulator::~SystemSimulator() {
#ifdef ENABLE_TRACE
  if (_vcd) {
    _vcd->close();
  }
#endif
}

void SystemSimulator::step(uint64_t cycles) {
  for (int i = 0; i < cycles; i++) {
    clock_tick();
  }
}

void SystemSimulator::run(uint64_t max_cycles) {
  uint64_t target = max_cycles > 0 ? max_cycles : _timeout;
  while (_time_counter < target && !_terminate) {
    clock_tick();
    check_termination();
  }

  if (_verbose) {
    std::cout << "\nSimulation completed after " << _time_counter
              << " counts of timer\n";
  }
}

void SystemSimulator::save_trace(const std::string &filename) {
  _trace->save(filename);
}

void SystemSimulator::check_termination() {}

} // namespace demu
#endif
