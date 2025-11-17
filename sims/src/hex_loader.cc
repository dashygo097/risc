#include "hex_loader.h"
#include <fstream>
#include <iomanip>
#include <iostream>
#include <sstream>

bool HexLoader::load_intel_hex(const std::string &filename,
                               std::vector<uint8_t> &data,
                               uint32_t &base_addr) {
  std::ifstream file(filename);
  if (!file.is_open()) {
    std::cerr << "Failed to open HEX file: " << filename << std::endl;
    return false;
  }

  data.clear();
  base_addr = 0;
  uint32_t extended_addr = 0;
  std::string line;

  while (std::getline(file, line)) {
    if (line.empty() || line[0] != ':')
      continue;

    // Parse Intel HEX record
    int byte_count = std::stoi(line.substr(1, 2), nullptr, 16);
    int address = std::stoi(line.substr(3, 4), nullptr, 16);
    int record_type = std::stoi(line.substr(7, 2), nullptr, 16);

    if (record_type == 0x00) { // Data record
      uint32_t full_addr = extended_addr + address;
      if (data.empty()) {
        base_addr = full_addr;
      }

      for (int i = 0; i < byte_count; i++) {
        int byte_val = std::stoi(line.substr(9 + i * 2, 2), nullptr, 16);
        data.push_back(static_cast<uint8_t>(byte_val));
      }
    } else if (record_type == 0x04) { // Extended linear address
      extended_addr = std::stoi(line.substr(9, 4), nullptr, 16) << 16;
    } else if (record_type == 0x01) { // End of file
      break;
    }
  }

  return !data.empty();
}

bool HexLoader::load_verilog_hex(const std::string &filename,
                                 std::vector<uint32_t> &data) {
  std::ifstream file(filename);
  if (!file.is_open()) {
    std::cerr << "Failed to open HEX file: " << filename << std::endl;
    return false;
  }

  data.clear();
  std::string line;

  while (std::getline(file, line)) {
    // Skip comments and empty lines
    if (line.empty() || line[0] == '/' || line[0] == '#')
      continue;

    // Remove whitespace
    line.erase(std::remove_if(line.begin(), line.end(), ::isspace), line.end());

    if (line.empty())
      continue;

    try {
      uint32_t value = std::stoul(line, nullptr, 16);
      data.push_back(value);
    } catch (const std::exception &e) {
      std::cerr << "Error parsing line: " << line << std::endl;
      return false;
    }
  }

  return !data.empty();
}
