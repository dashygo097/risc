#include "demu/sim.hh"
#include "demu/elf_loader.hh"
#include "demu/logger.hh"

namespace demu {

DemuSimulator::DemuSimulator(bool enabled_trace, int threads, int argc,
                             char **argv)
    : trace_enabled_(enabled_trace) {

  context_ = std::make_unique<VerilatedContext>();
  context_->debug(0);
  context_->randReset(2);
  context_->threads(threads);
  context_->commandArgs(argc, argv);

  if (trace_enabled_) {
    context_->traceEverOn(true);
  }

  dut_ = std::make_unique<system_t>(context_.get(), "TOP");

  device_manager_ = std::make_unique<hal::DeviceManager>();

  config_ = std::make_unique<RiscConfig>();
  config_->dump();
  config_->validate();
}

DemuSimulator::~DemuSimulator() {
  dut_->final();

#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->close();
  }
#endif

#ifdef VM_COVERAGE
  Verilated::mkdir("logs");
  context_->coveragep()->write("logs/coverage.dat");
  DEMU_INFO("Coverage written to logs/coverage.dat");
#endif
}

auto DemuSimulator::load_bin(const std::string &filename, addr_t base_addr)
    -> bool {
  auto *device = device_manager_->find_device_for_address(base_addr);
  if (!device) {
    DEMU_ERROR("No device mapped at address 0x{:08x}", base_addr);
    return false;
  }

  auto *alloc = device->allocator();
  if (!alloc) {
    DEMU_ERROR("Device '{}' has no memory allocator", device->name());
    return false;
  }

  if (alloc->load_binary(filename, base_addr)) {
    return true;
  }

  DEMU_ERROR("Failed to load binary: {}", filename);
  return false;
}

auto DemuSimulator::load_elf(const std::string &filename) -> bool {
  std::vector<ELFSection> sections;
  uint32_t entry_point = 0;

  if (!ELFLoader::load(sections, entry_point, filename)) {
    DEMU_ERROR("Failed to parse ELF: {}", filename);
    return false;
  }

  DEMU_INFO("ELF entry point: 0x{:08x}, {} loadable sections", entry_point,
            sections.size());

  for (const auto &section : sections) {
    if (section.data.empty()) {
      continue;
    }

    auto *device = device_manager_->find_device_for_address(section.addr);
    if (!device) {
      DEMU_ERROR("No device mapped at 0x{:08x} for section '{}'", section.addr,
                 section.name);
      return false;
    }

    auto *alloc = device->allocator();
    if (!alloc) {
      DEMU_ERROR("Device '{}' has no allocator for section '{}'",
                 device->name(), section.name);
      return false;
    }

    for (size_t i = 0; i < section.data.size(); ++i) {
      addr_t addr = section.addr + static_cast<addr_t>(i);
      alloc->write_byte(addr, section.data[i]);
    }

    DEMU_INFO("Loaded section '{}' at 0x{:08x} ({} bytes)", section.name,
              section.addr, section.data.size());
  }

  DEMU_INFO("ELF loaded successfully. Entry: 0x{:08x}", entry_point);
  return true;
}

void DemuSimulator::init() {
  DEMU_INFO("DEMU Simulator Initializing...");

  register_devices();
  device_manager_->dump_device_map();

#ifdef ENABLE_TRACE
  if (trace_enabled_) {
    Verilated::mkdir("logs");
    vcd_ = std::make_unique<VerilatedVcdC>();
    dut_->trace(vcd_.get(), 99);
    vcd_->open(("logs/demu_" + std::string(ISA_NAME) + "_trace.vcd").c_str());
    DEMU_DEBUG("VCD tracing enabled: logs/demu_{}_trace.vcd", ISA_NAME);
  }
#endif
}

void DemuSimulator::reset() {
  DEMU_INFO("Resetting...");
  dut_->reset = 1;
  dut_->clock = 0;
  dut_->eval();
  dut_->clock = 1;
  dut_->eval();
  dut_->reset = 0;
  dut_->eval();

  device_manager_->reset();

  _l1_icache_misses = 0;
  _l1_dcache_accesses = 0;
  _l1_dcache_misses = 0;

  _terminate = false;
  _register_values.clear();

  on_reset();
  DEMU_INFO("System Reset Complete. PC: 0x{:08x}",
            static_cast<addr_t>(dut_->debug_pc))
}

void DemuSimulator::step(uint64_t cycles) {
  for (uint64_t i = 0; i < cycles; i++) {
    clock_tick();
  }
}

void DemuSimulator::run(uint64_t max_cycles) {
  DEMU_INFO("Starting DEMU Simulation...");
  uint64_t target = max_cycles > 0 ? max_cycles : timeout_;

  auto start_time = std::chrono::high_resolution_clock::now();
  on_init();
  while (cycle_count() < target && !_terminate) {
    clock_tick();
  }
  on_exit();
  auto end_time = std::chrono::high_resolution_clock::now();

  auto duration = std::chrono::duration_cast<std::chrono::microseconds>(
                      end_time - start_time)
                      .count();

  DEMU_INFO("Simulation completed!")
  DEMU_INFO("  With {} cycles, {} instructions, IPC: {:.2f} "
            "after {} ms",
            cycle_count(), instret_count(), ipc(), duration / 1000.0);

  DEMU_INFO("  L1 Icache Hit Rate: {:.2f} %", l1_icache_hit_rate() * 100);
  DEMU_INFO("  L1 Dcache Hit Rate: {:.2f} %", l1_dcache_hit_rate() * 100);
}

void DemuSimulator::dump_registers() const {
  DEMU_INFO("Register Dump:");
  for (int i = 0; i < NUM_GPRS; i += 4) {
    DEMU_INFO(
        "  x{:02d}={:08x}  x{:02d}={:08x}  x{:02d}={:08x}  x{:02d}={:08x}", i,
        reg(i), i + 1, reg(i + 1), i + 2, reg(i + 2), i + 3, reg(i + 3));
  }
}

void DemuSimulator::dump_memory(addr_t start, size_t size) const {
  const auto *device = device_manager_->find_device_for_address(start);
  if (!device) {
    HAL_WARN("Invalid memory dump address: 0x{:0x8x}", start);
    return;
  }
  device->dump(start, size);
}

void DemuSimulator::clock_tick() {
  DEMU_CPU_TICK(cycle_count());

  context_->timeInc(1);

  dut_->clock = 0;
  device_manager_->handle_ports();
  dut_->eval();

#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->dump(context_->time());
  }
#endif

  context_->timeInc(1);

  dut_->clock = 1;
  dut_->eval();

  device_manager_->clock_tick();
  handle_cache_profiling();

  if (dut_->debug_reg_addr != 0) {
    if (dut_->debug_reg_we) {
      auto reg_data = static_cast<word_t>(dut_->debug_reg_data);
      _register_values[dut_->debug_reg_addr] = reg_data;
      DEMU_REG_WRITE(dut_->debug_reg_addr, reg_data);
    }
  }

  if (show_pipeline_) {
    DEMU_DEBUG("PIPE | IF:{:08x} ID:{:08x} EX:{:08x} MEM:{:08x} WB:{:08x}",
               dut_->debug_if_instr, dut_->debug_id_instr, dut_->debug_ex_instr,
               dut_->debug_mem_instr, dut_->debug_wb_instr);
  }

  if (dut_->debug_branch_taken) {
    DEMU_TRACE("[BRANCH] Taken: 0x{:08x} -> 0x{:08x}",
               dut_->debug_branch_source, dut_->debug_branch_target);
  }

  if (dut_->debug_instret) {
    Instruction inst(static_cast<instr_t>(dut_->debug_instr));
    DEMU_INFO("RETIRE | Cycle {:6d} | PC=0x{:08x} | Inst=0x{:08x} ({})",
              cycle_count(), static_cast<addr_t>(dut_->debug_pc),
              dut_->debug_instr, inst.to_string());
  }
  on_clock_tick();

#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->dump(context_->time());
  }
#endif
}

void DemuSimulator::handle_cache_profiling() {
  if (dut_->debug_l1_icache_access) {
    _l1_icache_accesses++;
    if (dut_->debug_l1_icache_miss) {
      _l1_icache_misses++;
    }
  }

  if (dut_->debug_l1_dcache_access) {
    _l1_dcache_accesses++;
    if (dut_->debug_l1_dcache_miss) {
      _l1_dcache_misses++;
    }
  }
}

} // namespace demu
