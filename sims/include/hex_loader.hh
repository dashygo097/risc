#ifndef HEX_LOADER_H
#define HEX_LOADER_H

#include <cstdint>
#include <string>
#include <vector>

class HexLoader {
public:
  static bool load_intel_hex(const std::string &filename,
                             std::vector<uint8_t> &data, uint32_t &base_addr);

  static bool load_verilog_hex(const std::string &filename,
                               std::vector<uint32_t> &data);

private:
  static bool parse_intel_hex_line(const std::string &line,
                                   std::vector<uint8_t> &data, uint32_t &addr);
};

#endif // HEX_LOADER_H
