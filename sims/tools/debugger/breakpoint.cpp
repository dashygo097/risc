#include "breakpoint.hh"
#include <fmt/format.h>

namespace demu::dbg {

auto BreakpointManager::add_breakpoint(BreakType type, uint64_t value)
    -> uint32_t {
  uint32_t id = next_id_++;
  breakpoints_[id] = {id, type, value, true};
  return id;
}

auto BreakpointManager::add_watchpoint(uint32_t addr, uint32_t size,
                                       WatchType type) -> uint32_t {
  uint32_t id = next_id_++;
  watchpoints_[id] = {id, addr, size, type, true};
  return id;
}

auto BreakpointManager::remove(uint32_t id) -> bool {
  if (breakpoints_.erase(id)) {
    return true;
  }
  if (watchpoints_.erase(id)) {
    return true;
  }
  return false;
}

auto BreakpointManager::enable(uint32_t id) -> bool {
  auto bit = breakpoints_.find(id);
  if (bit != breakpoints_.end()) {
    bit->second.enabled = true;
    return true;
  }
  auto wit = watchpoints_.find(id);
  if (wit != watchpoints_.end()) {
    wit->second.enabled = true;
    return true;
  }
  return false;
}

auto BreakpointManager::disable(uint32_t id) -> bool {
  auto bit = breakpoints_.find(id);
  if (bit != breakpoints_.end()) {
    bit->second.enabled = false;
    return true;
  }
  auto wit = watchpoints_.find(id);
  if (wit != watchpoints_.end()) {
    wit->second.enabled = false;
    return true;
  }
  return false;
}

auto BreakpointManager::check_breakpoint(uint64_t pc, uint64_t cycle,
                                         uint64_t instret) const -> bool {
  for (const auto &[id, bp] : breakpoints_) {
    if (!bp.enabled) {
      continue;
    }
    switch (bp.type) {
    case BreakType::PC:
      if (pc == bp.value) {
        return true;
      }
      break;
    case BreakType::CYCLE:
      if (cycle >= bp.value) {
        return true;
      }
      break;
    case BreakType::INSTRET:
      if (instret >= bp.value) {
        return true;
      }
      break;
    }
  }
  return false;
}

auto BreakpointManager::check_watchpoint_read(uint32_t addr,
                                              uint32_t size) const -> bool {
  for (const auto &[id, wp] : watchpoints_) {
    if (!wp.enabled) {
      continue;
    }
    if (wp.type == WatchType::WRITE) {
      continue;
    }
    if (ranges_overlap(addr, size, wp.address, wp.size)) {
      return true;
    }
  }
  return false;
}

auto BreakpointManager::check_watchpoint_write(uint32_t addr,
                                               uint32_t size) const -> bool {
  for (const auto &[id, wp] : watchpoints_) {
    if (!wp.enabled) {
      continue;
    }
    if (wp.type == WatchType::READ) {
      continue;
    }
    if (ranges_overlap(addr, size, wp.address, wp.size)) {
      return true;
    }
  }
  return false;
}

auto BreakpointManager::ranges_overlap(uint32_t a_start, uint32_t a_size,
                                       uint32_t b_start, uint32_t b_size) const
    -> bool {
  return a_start < (b_start + b_size) && b_start < (a_start + a_size);
}

auto BreakpointManager::list() const -> std::string {
  if (breakpoints_.empty() && watchpoints_.empty()) {
    return "No breakpoints or watchpoints set.\n";
  }

  std::string result;

  for (const auto &[id, bp] : breakpoints_) {
    const char *type_str = "???";
    switch (bp.type) {
    case BreakType::PC:
      type_str = "PC";
      break;
    case BreakType::CYCLE:
      type_str = "CYCLE";
      break;
    case BreakType::INSTRET:
      type_str = "INSTRET";
      break;
    }
    result += fmt::format("  #{:<4d} {:>7s} = 0x{:x}  [{}]\n", id, type_str,
                          bp.value, bp.enabled ? "enabled" : "disabled");
  }

  for (const auto &[id, wp] : watchpoints_) {
    const char *type_str = "???";
    switch (wp.type) {
    case WatchType::READ:
      type_str = "r";
      break;
    case WatchType::WRITE:
      type_str = "w";
      break;
    case WatchType::READWRITE:
      type_str = "rw";
      break;
    }
    result += fmt::format("  #{:<4d} WATCH 0x{:08x} size={} type={}  [{}]\n",
                          id, wp.address, wp.size, type_str,
                          wp.enabled ? "enabled" : "disabled");
  }

  return result;
}

} // namespace demu::dbg
