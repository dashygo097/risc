#ifndef TRACE_H
#define TRACE_H

#include <cstdint>
#include <fstream>
#include <string>
#include <vector>

class ExecutionTrace {
public:
  struct TraceEntry {
    uint64_t cycle;
    uint32_t pc;
    uint32_t inst;
    std::string disasm;
    uint8_t rd;
    uint32_t rd_val;
    bool rd_written;
  };

  ExecutionTrace() = default;
  ~ExecutionTrace() = default;

  void add_entry(const TraceEntry &entry);
  void save(const std::string &filename) const;
  void clear();

  size_t size() const { return entries_.size(); }
  const TraceEntry &operator[](size_t idx) const { return entries_[idx]; }

private:
  std::vector<TraceEntry> entries_;
};

#endif // TRACE_H
