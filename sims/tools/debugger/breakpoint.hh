#pragma once

#include <cstdint>
#include <map>
#include <string>

namespace demu::dbg {

enum class BreakType { PC, CYCLE, INSTRET };
enum class WatchType { READ, WRITE, READWRITE };

struct Breakpoint {
  uint32_t id;
  BreakType type;
  uint64_t value;
  bool enabled;
};

struct Watchpoint {
  uint32_t id;
  uint32_t address;
  uint32_t size;
  WatchType type;
  bool enabled;
};

class BreakpointManager {
public:
  auto add_breakpoint(BreakType type, uint64_t value) -> uint32_t;
  auto add_watchpoint(uint32_t addr, uint32_t size, WatchType type) -> uint32_t;

  auto remove(uint32_t id) -> bool;
  auto enable(uint32_t id) -> bool;
  auto disable(uint32_t id) -> bool;

  [[nodiscard]] auto check_breakpoint(uint64_t pc, uint64_t cycle,
                                      uint64_t instret) const -> bool;
  [[nodiscard]] auto check_watchpoint_read(uint32_t addr, uint32_t size) const
      -> bool;
  [[nodiscard]] auto check_watchpoint_write(uint32_t addr, uint32_t size) const
      -> bool;

  [[nodiscard]] auto list() const -> std::string;

private:
  uint32_t next_id_{1};
  std::map<uint32_t, Breakpoint> breakpoints_;
  std::map<uint32_t, Watchpoint> watchpoints_;

  [[nodiscard]] auto ranges_overlap(uint32_t a_start, uint32_t a_size,
                                    uint32_t b_start, uint32_t b_size) const
      -> bool;
};

} // namespace demu::dbg
