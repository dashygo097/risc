#ifndef CPU_SIM_H
#define CPU_SIM_H

#include "Vrv32_cpu.h"
#include "verilated.h"
#include <cstdint>
#include <map>
#include <memory>
#include <string>

#ifdef ENABLE_TRACE
#include "verilated_vcd_c.h"
#endif

class Memory;
class ExecutionTrace;

class CPUSimulator {
public:
  CPUSimulator(bool enable_trace = false);
  ~CPUSimulator();

  bool load_hex(const std::string &filename);
  bool load_bin(const std::string &filename, uint32_t base_addr = 0);
  bool load_elf(const std::string &filename);

  void reset();
  void step(int cycles = 1);
  void run(uint64_t max_cycles = 0);
  void run_until(uint32_t pc);

  uint32_t get_pc() const;
  uint32_t get_reg(uint8_t reg) const;
  uint32_t read_mem(uint32_t addr) const;
  void write_mem(uint32_t addr, uint32_t data);

  uint64_t get_cycle_count() const { return cycle_count_; }
  uint64_t get_inst_count() const { return inst_count_; }
  double get_ipc() const;

  void set_verbose(bool verbose) { verbose_ = verbose; }
  void set_timeout(uint64_t timeout) { timeout_ = timeout; }
  void enable_profiling(bool enable) { profiling_ = enable; }

  void dump_registers() const;
  void dump_memory(uint32_t start, uint32_t size) const;
  void save_trace(const std::string &filename);

private:
  std::unique_ptr<Vrv32_cpu> dut_;
  std::unique_ptr<Memory> imem_;
  std::unique_ptr<Memory> dmem_;
  std::unique_ptr<ExecutionTrace> trace_;

#ifdef ENABLE_TRACE
  std::unique_ptr<VerilatedVcdC> vcd_;
#endif

  uint64_t time_counter_;
  uint64_t cycle_count_;
  uint64_t inst_count_;
  uint64_t timeout_;

  bool verbose_;
  bool profiling_;
  bool trace_enabled_;

  std::map<uint8_t, uint32_t> register_values_;
  std::map<uint32_t, uint64_t> pc_histogram_;

  void clock_tick();
  void update_stats();
  void check_termination();
};

#endif // CPU_SIM_H
