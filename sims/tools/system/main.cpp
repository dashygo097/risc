#include <cstdlib>
#include <cstring>
#include <demu.hh>
#include <iostream>
#include <string>

class SystemSimulatorTop final : public demu::SystemSimulator {
public:
  SystemSimulatorTop(bool enabled_trace = false)
      : SystemSimulator(enabled_trace) {}

protected:
  void register_devices() override {};
  void on_init() override {
    _imem->read_delay(1);
    _imem->write_delay(1);
    _dmem->read_delay(10);
    _dmem->write_delay(10);
  };
  void on_clock_tick() override {};
  void on_exit() override {};
  void on_reset() override {};

  demu::hal::axi::AXILiteSignals from_port(uint8_t port) override {
    demu::hal::axi::AXILiteSignals signals;
    switch (port) {
    case 0:
      MAP_AXIL_SIGNALS(signals, 0)
      break;
    case 1:
      MAP_AXIL_SIGNALS(signals, 1)
      break;
    default:
      break;
    }
    return signals;
  }
};

void print_usage(const char *prog) {
  std::cout << "Usage: " << prog << " [options] <program_file>\n\n";
  std::cout << "Options:\n";
  std::cout << "  -h, --help           Show this help message\n";
  std::cout << "  -v, --verbose        Enable verbose output\n";
  std::cout << "  -t, --trace          Enable VCD trace\n";
  std::cout << "  -c, --cycles <n>     Run for n cycles (0=unlimited)\n";
  std::cout << "  -b, --base <addr>    Binary load base address (hex)\n";
  std::cout << "  -m, --dump-mem <addr> <size>  Dump memory region\n";
  std::cout << "  -L12345,             Set log level ( 1=error, 2=warn, "
               "3=info, 4=debug, 5=trace)\n";
  std::cout << "\nSupported file formats:\n";
  std::cout << "  .bin                 Raw binary\n";
  std::cout << "  .elf                 ELF executable\n";
  std::cout << std::endl;
}

int main(int argc, char **argv) {
  if (argc < 2) {
    print_usage(argv[0]);
    return 1;
  }

  std::string program_file;
  bool verbose = false;
  bool enable_trace = false;
  uint64_t max_cycles = 0;
  uint32_t base_addr = 0;
  uint32_t dump_mem_addr = 0;
  uint32_t dump_mem_size = 0;
  bool dump_mem = false;
  spdlog::level::level_enum spdlog_level = spdlog::level::info;

  for (int i = 1; i < argc; i++) {
    std::string arg = argv[i];

    if (arg == "-h" || arg == "--help") {
      print_usage(argv[0]);
      return 0;
    } else if (arg == "-v" || arg == "--verbose") {
      verbose = true;
    } else if (arg == "-t" || arg == "--trace") {
      enable_trace = true;
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
    } else if (arg[0] == '-' && arg[1] == 'L') {
      int log_level = std::stoi(arg.substr(2));
      switch (log_level) {
      case 1:
        spdlog_level = spdlog::level::err;
        break;
      case 2:
        spdlog_level = spdlog::level::warn;
        break;
      case 3:
        spdlog_level = spdlog::level::info;
        break;
      case 4:
        spdlog_level = spdlog::level::debug;
        break;
      case 5:
        spdlog_level = spdlog::level::trace;
        break;
      default:
        std::cerr << "Unknown log level: " << log_level << std::endl;
      }
    } else if (arg[0] != '-') {
      program_file = arg;
    } else {
      std::cerr << "Unknown option: " << arg << std::endl;
      return 1;
    }
  }

  if (program_file.empty()) {
    std::cerr << "Error: No program file specified\n";
    print_usage(argv[0]);
    return 1;
  }

  demu::Logger::init(spdlog_level);
  SystemSimulatorTop sim(enable_trace);
  sim.verbose(verbose);

  sim.reset();

  bool loaded = false;
  if (program_file.substr(program_file.find_last_of(".") + 1) == "bin") {
    loaded = sim.load_bin(program_file, base_addr);
  } else if (program_file.substr(program_file.find_last_of(".") + 1) == "elf") {
    loaded = sim.load_elf(program_file);
  } else {
    std::cerr << "Error: Unsupported file format\n";
    return 1;
  }

  if (!loaded) {
    std::cerr << "Error: Failed to load program\n";
    return 1;
  }

  sim.run(max_cycles);

  if (dump_mem) {
    sim.dump_memory(dump_mem_addr, dump_mem_size);
  }

  return 0;
}
