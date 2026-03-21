#include "debugger.hh"
#include <cstdlib>
#include <demu/logger.hh>
#include <demu/sim.hh>
#include <iostream>
#include <string>

using namespace demu::isa;

class DemuDebuggerTop final : public demu::DemuSimulator {
public:
  DemuDebuggerTop(bool enabled_trace = false) : DemuSimulator(enabled_trace) {}

protected:
  void register_devices() override {
    const auto *imem_r = config_->find_region("imem");
    const auto *dmem_r = config_->find_region("dmem");
    const auto *uart_r = config_->find_region("uart");

    device_manager_->register_device<demu::hal::axi::AXILiteSRAM>(0, *imem_r);
    device_manager_->register_device<demu::hal::axi::AXILiteSRAM>(1, *dmem_r);
    device_manager_->register_device<demu::hal::axi::AXILiteSRAM>(2, *uart_r);

    device_manager_->register_handler(
        0, std::make_unique<demu::hal::axi::AXILitePortHandler>([this]() {
          demu::hal::axi::AXILiteSignals s;
          MAP_AXIL_SIGNALS(dut_, s, 0);
          return s;
        }));

    device_manager_->register_handler(
        1, std::make_unique<demu::hal::axi::AXILitePortHandler>([this]() {
          demu::hal::axi::AXILiteSignals s;
          MAP_AXIL_SIGNALS(dut_, s, 1);
          return s;
        }));

    device_manager_->register_handler(
        2, std::make_unique<demu::hal::axi::AXILitePortHandler>([this]() {
          demu::hal::axi::AXILiteSignals s;
          MAP_AXIL_SIGNALS(dut_, s, 2);
          return s;
        }));
  }

  void on_init() override {}
  void on_clock_tick() override {}
  void on_exit() override {}
  void on_reset() override {}
};

void print_usage(const char *prog) {
  std::cout << "Usage: " << prog << " [options] <program.bin>\n\n";
  std::cout << "Options:\n";
  std::cout << "  -h, --help       Show this help message\n";
  std::cout << "  -t, --trace      Enable VCD trace\n";
  std::cout << "  -L<1-5>          Log level (1=trace, 5=error)\n";
  std::cout << std::endl;
}

int main(int argc, char **argv) {
  if (argc < 2) {
    print_usage(argv[0]);
    return 1;
  }

  std::string program_file;
  bool enable_trace = false;
  spdlog::level::level_enum spdlog_level = spdlog::level::warn;

  for (int i = 1; i < argc; i++) {
    std::string arg = argv[i];

    if (arg == "-h" || arg == "--help") {
      print_usage(argv[0]);
      return 0;
    } else if (arg == "-t" || arg == "--trace") {
      enable_trace = true;
    } else if (arg.size() >= 3 && arg[0] == '-' && arg[1] == 'L') {
      int level = std::stoi(arg.substr(2));
      switch (level) {
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
      default:
        std::cerr << "Unknown log level: " << level << std::endl;
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
  DemuDebuggerTop sim(enable_trace);

  sim.init();
  sim.reset();

  std::string ext = program_file.substr(program_file.find_last_of('.') + 1);
  bool loaded = false;

  if (ext == "bin") {
    loaded = sim.load_bin(program_file, 0);
  } else if (ext == "elf") {
    loaded = sim.load_elf(program_file);
  } else {
    std::cerr << "Error: Unsupported file format: ." << ext << "\n";
    return 1;
  }

  if (!loaded) {
    std::cerr << "Error: Failed to load program\n";
    return 1;
  }

  demu::dbg::Debugger dbg(sim);
  dbg.repl();

  return 0;
}
