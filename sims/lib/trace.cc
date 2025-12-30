#include "rvsim/trace.hh"
#include <fstream>
#include <iomanip>
#include <iostream>

void ExecutionTrace::add_entry(const TraceEntry &entry) {
  _entries.push_back(entry);
}

void ExecutionTrace::save(const std::string &filename) const {
  std::ofstream file(filename);
  if (!file.is_open()) {
    std::cerr << "Failed to open trace file: " << filename << std::endl;
    return;
  }

  file << "Cycle,PC,Instruction,Disassembly,Reg,Value\n";

  for (const auto &entry : _entries) {
    file << std::dec << entry.cycle << ","
         << "0x" << std::hex << std::setw(8) << std::setfill('0') << entry.pc
         << ","
         << "0x" << std::hex << std::setw(8) << entry.inst << ","
         << entry.disasm << ",";

    if (entry.regwrite) {
      file << "x" << std::dec << (int)entry.rd << ","
           << "0x" << std::hex << std::setw(8) << entry.rd_val;
    } else {
      file << "-,-";
    }

    file << "\n";
  }

  file.close();
  std::cout << "Execution trace saved to " << filename << std::endl;
}

void ExecutionTrace::clear() { _entries.clear(); }
