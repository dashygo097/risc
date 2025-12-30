#pragma once

#include <cstdint>
#include <string>
#include <vector>

struct TraceEntry {
  uint64_t cycle;
  uint32_t pc;
  uint32_t inst;
  std::string disasm;
  uint8_t rd;
  uint32_t rd_val;
  bool rd_written;
};

class ExecutionTrace {
public:
  ExecutionTrace() = default;
  ~ExecutionTrace() = default;

  void add_entry(const TraceEntry &entry);
  void save(const std::string &filename) const;
  void clear();

  [[nodiscard]] const TraceEntry &operator[](size_t idx) const {
    return _entries[idx];
  }

  [[nodiscard]] size_t size() const noexcept { return _entries.size(); }

private:
  std::vector<TraceEntry> _entries;
};
