#ifdef ENABLE_SYSTEM

#include "demu/system_sim.hh"
#include "demu/logger.hh"

namespace demu {

SystemSimulator::SystemSimulator(bool enabled_trace)
    : _dut(std::make_unique<Vrv32i_system>()),
      _device_manager(std::make_unique<hal::DeviceManager>()),
      _trace_enabled(enabled_trace) {

  DEMU_INFO("System Simulator Initializing...");

  _imem = _device_manager->register_slave<hal::axi::AXILiteMemory>(
      0, "imem", 4 * 1024, 0x00000000);
  _dmem = _device_manager->register_slave<hal::axi::AXILiteMemory>(
      1, "dmem", 16 * 1024, 0x80000000);

  register_devices();
  _device_manager->dump_device_map();

#ifdef ENABLE_TRACE
  if (_trace_enabled) {
    Verilated::traceEverOn(true);
    _vcd = std::make_unique<VerilatedVcdC>();
    _dut->trace(_vcd.get(), 99);
    _vcd->open((std::string(ISA_NAME) + "_system.vcd").c_str());
    DEMU_DEBUG("VCD tracing enabled: {}_system.vcd", ISA_NAME);
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

bool SystemSimulator::load_bin(const std::string &filename, addr_t base_addr) {
  if (_imem->load_binary(filename, base_addr)) {
    return true;
  }
  DEMU_ERROR("System failed to load binary: {}", filename);
  return false;
}

bool SystemSimulator::load_elf(const std::string &filename) {
  DEMU_WARN("ELF loading not yet implemented for System mode.");
  return false;
}

void SystemSimulator::clock_tick() {
  DEMU_CPU_TICK(_cycle_count);

  _dut->clock = 0;
  handle_port(0);
  handle_port(1);
  _dut->eval();

#ifdef ENABLE_TRACE
  if (_vcd) {
    _vcd->dump(_time_counter++);
  }
#endif

  _device_manager->clock_tick();
  on_clock_tick();

  _dut->clock = 1;
  _dut->eval();

#ifdef ENABLE_TRACE
  if (_vcd) {
    _vcd->dump(_time_counter++);
  }
#endif
  _cycle_count++;
}

void SystemSimulator::reset() {
  DEMU_INFO("System Resetting...");
  _dut->reset = 1;
  _dut->clock = 0;
  _dut->eval();
  _dut->clock = 1;
  _dut->eval();
  _dut->reset = 0;
  _dut->eval();

  _device_manager->reset();

  _time_counter = 0;
  _cycle_count = 0;
  _terminate = false;

  on_reset();
  DEMU_INFO("System Reset Complete.");
}

void SystemSimulator::step(uint64_t cycles) {
  for (uint64_t i = 0; i < cycles; i++) {
    clock_tick();
  }
}

void SystemSimulator::run(uint64_t max_cycles) {
  DEMU_INFO("Starting System Simulation...");

  auto start_time = std::chrono::high_resolution_clock::now();
  on_init();
  while (!_terminate && (max_cycles == 0 || _cycle_count < max_cycles) &&
         (_timeout == 0 || _cycle_count < _timeout)) {
    clock_tick();
    check_termination();
  }
  on_exit();
  auto end_time = std::chrono::high_resolution_clock::now();

  auto duration = std::chrono::duration_cast<std::chrono::microseconds>(
                      end_time - start_time)
                      .count();

  DEMU_INFO("Simulation completed: {} cycles, "
            "after {} ms",
            _cycle_count, duration / 1000.0);
}

void SystemSimulator::dump_memory(addr_t start, size_t size) const {
  _device_manager->dump_memory(start, size);
}

void SystemSimulator::check_termination() {}

} // namespace demu
#endif
