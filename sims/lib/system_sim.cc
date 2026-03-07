#ifdef ENABLE_SYSTEM

#include "demu/system_sim.hh"
#include "demu/logger.hh"

namespace demu {

SystemSimulator::SystemSimulator(bool enabled_trace)
    : _trace_enabled(enabled_trace) {
  dut_ = std::make_unique<system_t>();
  device_manager_ = std::make_unique<hal::DeviceManager>();

  config_ = std::make_unique<RiscConfig>();
  config_->dump();
  config_->validate();
};

SystemSimulator::~SystemSimulator() {
#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->close();
  }
#endif
}

void SystemSimulator::init() {
  DEMU_INFO("System Simulator Initializing...");

  register_devices();
  device_manager_->dump_device_map();

#ifdef ENABLE_TRACE
  if (_trace_enabled) {
    Verilated::traceEverOn(true);
    vcd_ = std::make_unique<VerilatedVcdC>();
    dut_->trace(vcd_.get(), 99);
    vcd_->open((std::string(ISA_NAME) + "_system.vcd").c_str());
    DEMU_DEBUG("VCD tracing enabled: {}_system.vcd", ISA_NAME);
  }
#endif
}

bool SystemSimulator::load_bin(const std::string &filename, addr_t base_addr) {
  if (imem_->load_binary(filename, base_addr)) {
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

  dut_->clock = 0;
  device_manager_->handle_ports();
  dut_->eval();

#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->dump(_time_count++);
  }
#endif

  device_manager_->clock_tick();
  on_clock_tick();

  dut_->clock = 1;
  dut_->eval();

#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->dump(_time_count++);
  }
#endif
  _cycle_count++;
}

void SystemSimulator::reset() {
  DEMU_INFO("System Resetting...");
  dut_->reset = 1;
  dut_->clock = 0;
  dut_->eval();
  dut_->clock = 1;
  dut_->eval();
  dut_->reset = 0;
  dut_->eval();

  device_manager_->reset();

  _time_count = 0;
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

  DEMU_INFO("Simulation completed!")
  DEMU_INFO("  With {} cycles "
            "after {} ms",
            _cycle_count, duration / 1000.0);
}

void SystemSimulator::dump_memory(addr_t start, size_t size) const {
  const auto *slave = device_manager_->find_slave_for_address(start);

  if (!slave) {
    HAL_WARN("dump_memory: no device owns address 0x{:08X}", start);
    return;
  }

  slave->dump(start, size);
}

void SystemSimulator::check_termination() {}

} // namespace demu
#endif
