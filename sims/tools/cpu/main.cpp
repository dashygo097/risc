#include "demu/hal/peripheral/sram/signals.hh"
#include <cstdlib>
#include <cstring>
#include <demu.hh>
#include <demu/logger.hh>
#include <iostream>
#include <string>

using namespace demu::isa;

class CPUSimulatorTop final : public demu::CPUSimulator {
public:
  CPUSimulatorTop(bool enabled_trace = false) : CPUSimulator(enabled_trace) {}

protected:
  void register_devices() override {
    const auto *imem_r = config_->find_region("imem");
    const auto *dmem_r = config_->find_region("dmem");
    const auto *mmio_r = config_->find_region("uart");

    device_manager_->register_slave<demu::hal::sram::SRAM>(0, *imem_r);
    device_manager_->register_slave<demu::hal::sram::SRAM>(1, *dmem_r);
    device_manager_->register_slave<demu::hal::sram::SRAM>(2, *mmio_r);

    using iresp_data_t = std::array<word_t, 4>;
    using dresp_data_t = std::array<word_t, 4>;
    using mresp_data_t = word_t;
    using ImemPortHandler =
        demu::hal::sram::CacheReadOnlyPortHandler<iresp_data_t>;
    using DmemPortHandler = demu::hal::sram::CachePortHandler<dresp_data_t>;
    using MmioPortHandler = demu::hal::sram::CachePortHandler<mresp_data_t>;

    device_manager_->register_handler(
        0, std::make_unique<ImemPortHandler>([this]() {
          demu::hal::sram::CacheReadOnlySignals<iresp_data_t> s;
          s.req.valid = &dut_->imem_req_valid;
          s.req.ready = &dut_->imem_req_ready;
          s.req.addr = &dut_->imem_req_bits_addr;
          s.resp.valid = &dut_->imem_resp_valid;
          s.resp.ready = &dut_->imem_resp_ready;
          s.resp.data =
              reinterpret_cast<iresp_data_t *>(&dut_->imem_resp_bits_data_0);
          s.resp.hit = &dut_->imem_resp_bits_hit;

          return s;
        }));

    device_manager_->register_handler(
        1, std::make_unique<DmemPortHandler>([this]() {
          demu::hal::sram::CacheSignals<iresp_data_t> s;
          s.req.valid = &dut_->dmem_req_valid;
          s.req.ready = &dut_->dmem_req_ready;
          s.req.op = &dut_->dmem_req_bits_op;
          s.req.addr = &dut_->dmem_req_bits_addr;
          s.req.data =
              reinterpret_cast<dresp_data_t *>(&dut_->dmem_req_bits_data_0);
          s.resp.valid = &dut_->dmem_resp_valid;
          s.resp.ready = &dut_->dmem_resp_ready;
          s.resp.data =
              reinterpret_cast<dresp_data_t *>(&dut_->dmem_resp_bits_data_0);
          s.resp.hit = &dut_->dmem_resp_bits_hit;
          return s;
        }));

    device_manager_->register_handler(
        2, std::make_unique<MmioPortHandler>([this]() {
          demu::hal::sram::CacheSignals<mresp_data_t> s;
          s.req.valid = &dut_->mmio_req_valid;
          s.req.ready = &dut_->mmio_req_ready;
          s.req.op = &dut_->mmio_req_bits_op;
          s.req.addr = &dut_->mmio_req_bits_addr;
          s.req.data =
              reinterpret_cast<mresp_data_t *>(&dut_->mmio_req_bits_data);
          s.resp.valid = &dut_->mmio_resp_valid;
          s.resp.ready = &dut_->mmio_resp_ready;
          s.resp.data =
              reinterpret_cast<mresp_data_t *>(&dut_->mmio_resp_bits_data);
          s.resp.hit = &dut_->mmio_resp_bits_hit;
          return s;
        }));
  };
  void on_init() override {};
  void on_clock_tick() override {};
  void on_exit() override {};
  void on_reset() override {};
};

void print_usage(const char *prog) {
  std::cout << "Usage: " << prog << " [options] <program_file>\n\n";
  std::cout << "Options:\n";
  std::cout << "  -h, --help                    Show this help message\n";
  std::cout << "  -t, --trace                   Enable VCD trace\n";
  std::cout
      << "  -c, --cycles <n>              Run for n cycles (0=unlimited)\n";
  std::cout
      << "  -b, --base <addr>             Binary load base address (hex)\n";
  std::cout
      << "  -d, --dump-regs               Dump registers after execution\n";
  std::cout << "  -m, --dump-mem <addr> <size>  Dump memory region\n";
  std::cout
      << "  -p, --show-pipeline           Show pipeline state each cycle\n";
  std::cout
      << "  -L12345,                      Set log level (5=error, 4=warn, "
         "3=info, 2=debug, 1=trace)\n";
  std::cout << "\nSupported file formats:\n";
  std::cout << "  .bin      Raw binary\n";
  std::cout << "  .elf      ELF executable\n";
  std::cout << std::endl;
}

int main(int argc, char **argv) {
  if (argc < 2) {
    print_usage(argv[0]);
    return 1;
  }

  std::string program_file;
  bool enable_trace = false;
  bool dump_regs = false;
  bool show_pipeline = false;
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
    } else if (arg == "-t" || arg == "--trace") {
      enable_trace = true;
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
    } else if (arg == "-p" || arg == "--show-pipeline") {
      show_pipeline = true;
    } else if (arg[0] == '-' && arg[1] == 'L') {
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
  CPUSimulatorTop sim(enable_trace);
  sim.show_pipeline(show_pipeline);

  sim.init();
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

  if (dump_regs) {
    sim.dump_registers();
  }

  if (dump_mem) {
    sim.dump_memory(dump_mem_addr, dump_mem_size);
  }

  return 0;
}
