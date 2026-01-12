#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include "./isa/isa.hh"

namespace demu {
using namespace isa;

struct TraceEntry {
  uint64_t cycle;
  addr_t pc;
  instr_t inst;
  std::string disasm;
  uint8_t rd;
  word_t rd_val;
  bool regwrite;
};

class ExecutionTrace final {
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

} // namespace demu
