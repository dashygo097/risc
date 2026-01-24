#ifdef ENABLE_SYSTEM

#include "demu/system_sim.hh"

namespace demu {
SystemSimulator::SystemSimulator(bool enabled_trace)
    : _dut(std::make_unique<Vrv32i_system>()),
      _axi_bus(std::make_unique<hal::axi::AXILiteBusManager>()),
      _time_counter(0), _timeout(0), _trace_enabled(enabled_trace),
      _terminate(false), _verbose(false) {

  _imem = _axi_bus->register_slave<hal::axi::AXILiteMemory>(0, "imem", 4 * 1024,
                                                            0x00000000);
  _dmem = _axi_bus->register_slave<hal::axi::AXILiteMemory>(1, "dmem", 4 * 1024,
                                                            0x80000000);
  set_mem_delay();
  register_devices();

  printf("Device Map:\n");
  _axi_bus->dump_device_map();

#ifdef ENABLE_TRACE
  if (_trace_enabled) {
    Verilated::traceEverOn(true);
    _vcd = std::make_unique<VerilatedVcdC>();
    _dut->trace(_vcd.get(), 99);
    _vcd->open((std::string(ISA_NAME) + "_system.vcd").c_str());
  }
#endif

  reset();
}

SystemSimulator::~SystemSimulator() {
#ifdef ENABLE_TRACE
  if (_vcd) {
    _vcd->close();
  }
#endif
}

bool SystemSimulator::load_bin(const std::string &filename, addr_t base_addr) {
  return _imem->load_binary(filename, base_addr);
}

bool SystemSimulator::load_elf(const std::string &filename) { return false; }

void SystemSimulator::reset() {
  _dut->reset = 1;
  _dut->clock = 0;
  _dut->eval();
  _dut->clock = 1;
  _dut->eval();
  _dut->reset = 0;
  _dut->eval();

  _axi_bus->reset();

  _time_counter = 0;
  _terminate = false;

  on_reset();
}

void SystemSimulator::step(uint64_t cycles) {
  for (uint64_t i = 0; i < cycles; i++) {
    clock_tick();
  }
}

void SystemSimulator::run(uint64_t max_cycles) {
  uint64_t cycle = 0;
  while (!_terminate) {
    clock_tick();

    if (max_cycles > 0 && ++cycle >= max_cycles) {
      printf("Reached max cycles: %llu\n", max_cycles);
      break;
    }

    if (_timeout > 0 && _time_counter >= _timeout) {
      printf("Simulation timeout at cycle %llu\n", _time_counter);
      break;
    }

    check_termination();
  }
  on_exit();
}

void SystemSimulator::dump_memory(addr_t start, size_t size) const {
  auto *slave = _axi_bus->find_slave_for_address(start);
  if (!slave) {
    printf("No device owns address 0x%08x\n", start);
    return;
  }

  auto *mem = dynamic_cast<hal::axi::AXILiteMemory *>(slave);
  if (mem) {
    printf("Memory dump [0x%08x - 0x%08zx]:\n", start, start + size);
    addr_t offset = start - mem->base_address();
    byte_t *ptr = mem->get_ptr(start + offset);

    if (ptr) {
      for (size_t i = 0; i < size && (offset + i) < mem->address_range();
           i += 16) {
        printf("%08zx: ", start + i);

        for (size_t j = 0; j < 16 && (i + j) < size; j++) {
          printf("%02x ", ptr[i + j]);
        }

        printf(" |");
        for (size_t j = 0; j < 16 && (i + j) < size; j++) {
          byte_t c = ptr[i + j];
          printf("%c", (c >= 32 && c < 127) ? c : '.');
        }
        printf("|\n");
      }
    }
  }
}

void SystemSimulator::clock_tick() {
  _dut->clock = 0;
  handle_port(0);
  handle_port(1);
  _dut->eval();

#ifdef ENABLE_TRACE
  if (_vcd) {
    _vcd->dump(_time_counter++);
  }
#endif

  _dut->clock = 1;
  _dut->eval();

  _axi_bus->clock_tick();

#ifdef ENABLE_TRACE
  if (_vcd) {
    _vcd->dump(_time_counter++);
  }
#endif

  on_clock_tick();
}

} // namespace demu
#endif
