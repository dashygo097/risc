#include "demu/sim.hh"
#include "demu/logger.hh"

namespace demu {

SystemSimulator::SystemSimulator(bool enabled_trace)
    : trace_enabled_(enabled_trace) {
  dut_ = std::make_unique<system_t>();
  device_manager_ = std::make_unique<hal::DeviceManager>();

  config_ = std::make_unique<RiscConfig>();
  config_->dump();
  config_->validate();

  l1_icache_line_size_ = config_->l1i_line_words();
  l1_dcache_line_size_ = config_->l1d_line_words();
};

SystemSimulator::~SystemSimulator() {
#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->close();
  }
#endif
}

bool SystemSimulator::load_bin(const std::string &filename, addr_t base_addr) {
  if (device_manager_->get_slave_by_name<hal::axi::AXILiteSRAM>("imem")
          ->load_binary(filename, 0)) {
    return true;
  }
  DEMU_ERROR("System failed to load binary: {}", filename);
  return false;
}

bool SystemSimulator::load_elf(const std::string &filename) {
  DEMU_WARN("ELF loading not yet implemented for System mode.");
  return false;
}

void SystemSimulator::init() {
  DEMU_INFO("DEMU Simulator Initializing...");

  register_devices();
  device_manager_->dump_device_map();

#ifdef ENABLE_TRACE
  if (trace_enabled_) {
    Verilated::traceEverOn(true);
    vcd_ = std::make_unique<VerilatedVcdC>();
    dut_->trace(vcd_.get(), 99);
    vcd_->open((std::string(ISA_NAME) + "_system.vcd").c_str());
    DEMU_DEBUG("VCD tracing enabled: {}_system.vcd", ISA_NAME);
  }
#endif
}

void SystemSimulator::reset() {
  DEMU_INFO("Resetting...");
  dut_->reset = 1;
  dut_->clock = 0;
  dut_->eval();
  dut_->clock = 1;
  dut_->eval();
  dut_->reset = 0;
  dut_->eval();

  device_manager_->reset();

  _time_count = 0;
  _l1_icache_misses = 0;
  _l1_dcache_accesses = 0;
  _l1_dcache_misses = 0;

  _terminate = false;
  _register_values.clear();

  on_reset();
  DEMU_INFO("System Reset Complete. PC: 0x{:08x}",
            static_cast<addr_t>(dut_->debug_pc))
}

void SystemSimulator::step(uint64_t cycles) {
  for (uint64_t i = 0; i < cycles; i++) {
    clock_tick();
  }
}

void SystemSimulator::run(uint64_t max_cycles) {
  DEMU_INFO("Starting DEMU Simulation...");
  uint64_t target = max_cycles > 0 ? max_cycles : timeout_;

  auto start_time = std::chrono::high_resolution_clock::now();
  on_init();
  while (cycle_count() < target && !_terminate) {
    clock_tick();
    check_termination();
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

void SystemSimulator::dump_registers() const {
  DEMU_INFO("Register Dump:");
  for (int i = 0; i < NUM_GPRS; i += 4) {
    DEMU_INFO(
        "  x{:02d}={:08x}  x{:02d}={:08x}  x{:02d}={:08x}  x{:02d}={:08x}", i,
        reg(i), i + 1, reg(i + 1), i + 2, reg(i + 2), i + 3, reg(i + 3));
  }
}

void SystemSimulator::dump_memory(addr_t start, size_t size) const {
  const auto *slave = device_manager_->find_slave_for_address(start);
  if (!slave) {
    HAL_WARN("Invalid memory dump address: 0x{:0x8x}", start);
    return;
  }
  slave->dump(start, size);
}

void SystemSimulator::clock_tick() {
  DEMU_CPU_TICK(cycle_count());

  dut_->clock = 0;
  device_manager_->handle_ports();
  dut_->eval();

#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->dump(_time_count++);
  }
#endif

  device_manager_->clock_tick();
  handle_cache_profiling();

  if (dut_->debug_reg_addr != 0) {
    if (dut_->debug_reg_we) {
      word_t reg_data = static_cast<word_t>(dut_->debug_reg_data);
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

  if (dut_->debug_instr != BUBBLE) {
    Instruction inst(static_cast<instr_t>(dut_->debug_instr));
    DEMU_INFO("RETIRE | Cycle {:6d} | PC=0x{:08x} | Inst=0x{:08x} ({})",
              cycle_count(), static_cast<addr_t>(dut_->debug_pc),
              dut_->debug_instr, inst.to_string());
  }
  on_clock_tick();

  dut_->clock = 1;
  dut_->eval();

#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->dump(_time_count++);
  }
#endif
}

void SystemSimulator::handle_cache_profiling() {
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

void SystemSimulator::check_termination() {}

} // namespace demu
