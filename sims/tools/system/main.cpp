#include <cstdlib>
#include <cstring>
#include <demu.hh>
#include <iostream>
#include <string>

class SystemSimulatorTop : public demu::SystemSimulator {
public:
  SystemSimulatorTop(bool enabled_trace = false)
      : SystemSimulator(enabled_trace) {}

private:
  void register_devices() override {};
  void check_termination() override {};
  void on_clock_tick() override {};
  void on_exit() override {};
  void on_reset() override {};

  demu::hal::AXISignals from_port(uint8_t port) override {
    demu::hal::AXISignals signals;

    switch (port) {
    case 0:
      MAP_AXI_SIGNALS(signals, 0) break;
    case 1:
      MAP_AXI_SIGNALS(signals, 1) break;
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

  SystemSimulatorTop sim(enable_trace);
  sim.verbose(verbose);

  std::cout << "Resetting System..." << std::endl;
  sim.reset();

  std::cout << "Loading program: " << program_file << std::endl;

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

  std::cout << "Running simulation";
  if (max_cycles > 0) {
    std::cout << " for " << max_cycles << " cycles";
  }
  std::cout << "..." << std::endl;

  auto start_time = std::chrono::high_resolution_clock::now();
  sim.run(max_cycles);
  auto end_time = std::chrono::high_resolution_clock::now();

  auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(
                      end_time - start_time)
                      .count();

  std::cout << "\n========================================\n";
  std::cout << "Simulation Statistics\n";
  std::cout << "========================================\n";
  std::cout << "Runtime:      " << duration << " ms\n";
  std::cout << "========================================\n\n";

  if (dump_mem) {
    sim.dump_memory(dump_mem_addr, dump_mem_size);
  }

  if (enable_trace) {
    std::cout << "Trace saved!\n";
  }

  return 0;
}
