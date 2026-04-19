#include "demu/elf_loader.hh"
#include "gdb_ref_model.hh"
#include "ref_model.hh"
#include <cstdlib>
#include <cstring>
#include <demu.hh>
#include <fstream>
#include <iostream>
#include <vector>

using namespace demu::isa;

class DemuSimulatorDiff final : public demu::DemuSimulator {
public:
  uint32_t entry_point_ = config_->ifu().reset_vector();

  explicit DemuSimulatorDiff(
      std::unique_ptr<demu::difftest::IRefModel> ref_model,
      bool enabled_trace = false, int threads = 1, int argc = 0,
      char **argv = nullptr)
      : DemuSimulator(enabled_trace, threads, argc, argv),
        ref_model_(std::move(ref_model)) {}

  auto load_bin(const std::string &filename, addr_t base_addr = 0) -> bool {
    entry_point_ = base_addr;
    bool ok = DemuSimulator::load_bin(filename, base_addr);
    if (ok) {
      std::ifstream file(filename, std::ios::binary);
      std::vector<uint8_t> buf(std::istreambuf_iterator<char>(file), {});
      ref_model_->sync_memory(base_addr, buf.size(), buf.data());
    }
    return ok;
  }

  auto load_elf(const std::string &filename) -> bool {
    bool ok = DemuSimulator::load_elf(filename);
    if (ok) {
      std::vector<demu::ELFSection> sections;
      demu::ELFLoader::load(sections, entry_point_, filename);
      for (const auto &sec : sections) {
        if (!sec.data.empty()) {
          ref_model_->sync_memory(sec.addr, sec.data.size(), sec.data.data());
        }
      }
      DEMU_INFO("Difftest: Synced ELF memory. Captured Entry PC: 0x{:08x}",
                entry_point_);
    }
    return ok;
  }

  void sync_ref_state() {
    ref_model_->set_pc(entry_point_);
    for (int i = 0; i < NUM_GPRS; i++) {
      ref_model_->set_reg(i, 0);
    }
    ref_model_->push_state();
    DEMU_INFO("Difftest: Synchronized Initial State to REF Model (PC=0x{:08x})",
              entry_point_);
  }

protected:
  void register_devices() override {
    register_port<0, demu::hal::axif::AXIFullPortHandler,
                  demu::hal::axif::AXIFullSRAM>("imem");
    register_port<1, demu::hal::axif::AXIFullPortHandler,
                  demu::hal::axif::AXIFullSRAM>("dmem");
    register_port<2, demu::hal::axif::AXIFullPortHandler,
                  demu::hal::axif::AXIFullUART>("uart");

#if defined(__ISA_RV32I__) || defined(__ISA_RV32IM__)
    register_port<3, demu::hal::axif::AXIFullPortHandler,
                  demu::hal::axif::AXIFullCLINT>(
        "clint", config_->freq(), timer_irq_.get(), soft_irq_.get());
#endif
  };

  void on_reset() override {}

  void on_clock_tick() override {
    if (__builtin_expect(static_cast<bool>(dut_->debug_instret), 0)) {
      ref_model_->pull_state();
      addr_t ref_pc = ref_model_->get_pc();
      addr_t dut_pc = pc();

      if (ref_pc != dut_pc) {
        DEMU_ERROR("Difftest PC Mismatch! | DUT: 0x{:08x} | REF: 0x{:08x}",
                   dut_pc, ref_pc);
        _terminate = true;
        return;
      }

      ref_model_->step(1);
      ref_model_->pull_state();

      for (int i = 0; i < NUM_GPRS; i++) {
        word_t ref_val = ref_model_->get_reg(i);
        word_t dut_val = reg(i);
        if (ref_val != dut_val) {
          DEMU_ERROR(
              "Difftest GPR[x{:02d}] Mismatch! | DUT: 0x{:08x} | REF: 0x{:08x}",
              i, dut_val, ref_val);
          _terminate = true;
          return;
        }
      }
    }
  }

private:
  std::unique_ptr<demu::difftest::IRefModel> ref_model_;

  [[nodiscard]] auto check_state() -> bool {
    bool match = true;
    addr_t ref_pc = ref_model_->get_pc();
    addr_t dut_pc = pc();

    if (ref_pc != dut_pc) {
      DEMU_ERROR("Difftest PC Mismatch! | DUT: 0x{:08x} | REF: 0x{:08x}",
                 dut_pc, ref_pc);
      match = false;
    }

    for (int i = 0; i < NUM_GPRS; i++) {
      word_t ref_val = ref_model_->get_reg(i);
      word_t dut_val = reg(i);
      if (ref_val != dut_val) {
        DEMU_ERROR(
            "Difftest GPR[x{:02d}] Mismatch! | DUT: 0x{:08x} | REF: 0x{:08x}",
            i, dut_val, ref_val);
        match = false;
      }
    }
    return match;
  }
};

void print_usage(const char *prog) {
  std::cout << R"(________  _______   _____ ______   ___  ___     
|\   ___ \|\  ___ \ |\   _ \  _   \|\  \|\  \    
\ \  \_|\ \ \   __/|\ \  \\\__\ \  \ \  \\\  \   
 \ \  \ \\ \ \  \_|/_\ \  \\|__| \  \ \  \\\  \  
  \ \  \_\\ \ \  \_|\ \ \  \    \ \  \ \  \\\  \ 
   \ \_______\ \_______\ \__\    \ \__\ \_______\
    \|_______|\|_______|\|__|     \|__|\|_______|
                                                 
                                                 )"
            << "\n";

  std::cout << "Usage: " << prog << " [options] <program_file>\n\n";
  std::cout << "Options:\n";
  std::cout << "  -h, --help                    Show this help message\n";
  std::cout << "  -R, --ref-so <path|gdb>       Path to Reference Model .so OR "
               "type 'gdb' for QEMU TCP\n";
  std::cout << "  -t, --trace                   Enable VCD trace\n";
  std::cout << "  -T, --threads <n>             Number of Verilator threads "
               "(default: 1)\n";
  std::cout
      << "  -c, --cycles <n>              Run for n cycles (0=unlimited)\n";
  std::cout
      << "  -d, --dump-regs               Dump registers after execution\n";
  std::cout << "  -m, --dump-mem <addr> <size>  Dump memory region\n";
  std::cout << "  -L12345,                      Set log level (5=err, 4=warn, "
               "3=info, 2=debug, 1=trace)\n";
}

auto main(int argc, char **argv) -> int {
  if (argc < 2) {
    print_usage(argv[0]);
    return 1;
  }

  std::string program_file;
  std::string ref_so_path;
  bool enable_trace = false;
  bool dump_regs = false;
  bool dump_mem = false;
  int threads = 1;
  uint64_t max_cycles = 0;
  uint32_t base_addr = 0;
  uint32_t dump_mem_addr = 0;
  uint32_t dump_mem_size = 0;
  spdlog::level::level_enum spdlog_level = spdlog::level::info;

  for (int i = 1; i < argc; i++) {
    std::string arg = argv[i];

    if (arg == "-h" || arg == "--help") {
      print_usage(argv[0]);
      return 0;
    } else if (arg == "-R" || arg == "--ref-so") {
      if (i + 1 < argc) {
        ref_so_path = argv[++i];
      }
    } else if (arg == "-t" || arg == "--trace") {
      enable_trace = true;
    } else if (arg == "-T" || arg == "--threads") {
      if (i + 1 < argc) {
        threads = std::stoi(argv[++i]);
      }
    } else if (arg == "-d" || arg == "--dump-regs") {
      dump_regs = true;
    } else if (arg == "-c" || arg == "--cycles") {
      if (i + 1 < argc) {
        max_cycles = std::stoull(argv[++i]);
      }
    } else if (arg == "-b" || arg == "--base") {
      if (i + 1 < argc) {
        base_addr = std::stoul(argv[++i], nullptr, 16);
      }
    } else if (arg == "-m" || arg == "--dump-mem") {
      if (i + 2 < argc) {
        dump_mem_addr = std::stoul(argv[++i], nullptr, 16);
        dump_mem_size = std::stoul(argv[++i], nullptr, 16);
        dump_mem = true;
      }
    } else if (arg[0] == '-' && arg.length() > 1 && arg[1] == 'L') {
      int log_level = std::stoi(arg.substr(2));
      switch (log_level) {
      case 1:
        spdlog_level = spdlog::level::trace;
        break;
      case 2:
        spdlog_level = spdlog::level::debug;
        break;
      case 3:
        spdlog_level = spdlog::level::info;
        break;
      case 4:
        spdlog_level = spdlog::level::warn;
        break;
      case 5:
        spdlog_level = spdlog::level::err;
        break;
      }
    } else if (arg[0] == '+') {
      continue;
    } else if (arg[0] != '-') {
      program_file = arg;
    }
  }

  demu::Logger::init(spdlog_level);

  if (program_file.empty()) {
    std::cerr << "Error: No program file specified\n";
    return 1;
  }

#ifdef REF_SO_PATH
  if (ref_so_path.empty()) {
    ref_so_path = REF_SO_PATH;
  }
#endif

  if (ref_so_path.empty()) {
    std::cerr << "Error: You must specify the reference model SO path using "
                 "--ref-so <path>\n";
    return 1;
  }

  auto ref = demu::difftest::create_ref_model(ref_so_path);
  if (!ref || !ref->init()) {
    std::cerr << "Error: Failed to initialize Reference Model.\n";
    return 1;
  }

  DemuSimulatorDiff sim(std::move(ref), enable_trace, threads, argc, argv);
  sim.init();

  sim.reset();

  bool loaded = false;
  if (program_file.find(".bin") != std::string::npos) {
    loaded = sim.load_bin(program_file, base_addr);
  } else if (program_file.find(".elf") != std::string::npos) {
    loaded = sim.load_elf(program_file);
  } else {
    std::cerr << "Error: Unsupported file format\n";
    return 1;
  }

  if (!loaded) {
    std::cerr << "Error: Failed to load program\n";
    return 1;
  }

  sim.sync_ref_state();
  sim.run(max_cycles);

  if (dump_regs) {
    sim.dump_registers();
  }
  if (dump_mem) {
    sim.dump_memory(dump_mem_addr, dump_mem_size);
  }

  return 0;
}
