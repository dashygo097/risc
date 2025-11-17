#ifndef ELF_LOADER_H
#define ELF_LOADER_H

#include <cstdint>
#include <string>
#include <vector>

class Memory;

class ELFLoader {
public:
  struct Section {
    std::string name;
    uint32_t addr;
    uint32_t size;
    std::vector<uint8_t> data;
  };

  static bool load(const std::string &filename, Memory &mem);
  static bool load(const std::string &filename, std::vector<Section> &sections,
                   uint32_t &entry_point);

private:
  static bool is_elf(const std::string &filename);
};

#endif // ELF_LOADER_H
