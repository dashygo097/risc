#pragma once

#include "breakpoint.hh"
#include <cstdint>
#include <demu/sim.hh>
#include <functional>
#include <set>
#include <string>
#include <vector>

namespace demu::dbg {

class Debugger {
public:
  explicit Debugger(demu::DemuSimulator &sim);

  void repl();

private:
  demu::DemuSimulator &sim_;
  BreakpointManager bp_mgr_;
  std::set<uint8_t> auto_display_regs_;
  bool running_{false};

  struct Command {
    std::string name;
    std::string alias;
    std::string brief;
    std::string usage;
    std::function<void(const std::vector<std::string> &)> handler;
  };

  std::vector<Command> commands_;

  void register_commands();
  auto find_command(const std::string &input) -> Command *;

  auto tokenize(const std::string &line) -> std::vector<std::string>;
  auto parse_number(const std::string &s) -> uint64_t;
  auto parse_reg(const std::string &s) -> int;

  void print_stop_banner();
  void print_registers();
  void print_auto_display();

  void cmd_help(const std::vector<std::string> &args);
  void cmd_run(const std::vector<std::string> &args);
  void cmd_continue(const std::vector<std::string> &args);
  void cmd_step(const std::vector<std::string> &args);
  void cmd_stepi(const std::vector<std::string> &args);
  void cmd_break(const std::vector<std::string> &args);
  void cmd_watch(const std::vector<std::string> &args);
  void cmd_delete(const std::vector<std::string> &args);
  void cmd_enable(const std::vector<std::string> &args);
  void cmd_disable(const std::vector<std::string> &args);
  void cmd_info(const std::vector<std::string> &args);
  void cmd_print(const std::vector<std::string> &args);
  void cmd_pipeline(const std::vector<std::string> &args);
  void cmd_display(const std::vector<std::string> &args);
  void cmd_undisplay(const std::vector<std::string> &args);
  void cmd_memory(const std::vector<std::string> &args);
  void cmd_reset(const std::vector<std::string> &args);
  void cmd_quit(const std::vector<std::string> &args);
};

} // namespace demu::dbg
