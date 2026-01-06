#include "demu/elf_loader.hh"
#include <cstring>
#include <fstream>
#include <iostream>

namespace demu {
bool ELFLoader::is_elf(const std::string &filename) {
  std::ifstream file(filename, std::ios::binary);
  if (!file.is_open())
    return false;

  uint8_t magic[4];
  file.read(reinterpret_cast<char *>(magic), 4);

  return (magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' &&
          magic[3] == 'F');
}

bool ELFLoader::load(hardware::Memory &mem, const std::string &filename) {
  if (!is_elf(filename)) {
    std::cerr << "Not a valid ELF file" << std::endl;
    return false;
  }

  std::ifstream file(filename, std::ios::binary);
  if (!file.is_open()) {
    std::cerr << "Failed to open ELF file: " << filename << std::endl;
    return false;
  }

  ELF32_Header elf_header;
  file.read(reinterpret_cast<char *>(&elf_header), sizeof(elf_header));

  if (elf_header.e_machine != 0xF3) {
    std::cerr << "Not a RISC-V ELF file" << std::endl;
    return false;
  }

  file.seekg(elf_header.e_phoff);

  for (int i = 0; i < elf_header.e_phnum; i++) {
    ELF32_ProgramHeader ph;
    file.read(reinterpret_cast<char *>(&ph), sizeof(ph));

    if (ph.p_type == 1 && ph.p_filesz > 0) {
      std::vector<uint8_t> data(ph.p_filesz);
      file.seekg(ph.p_offset);
      file.read(reinterpret_cast<char *>(data.data()), ph.p_filesz);

      for (size_t j = 0; j < data.size(); j++) {
        mem.write_byte(ph.p_paddr + j, data[j]);
      }

      std::cout << "Loaded segment: addr=0x" << std::hex << ph.p_paddr
                << " size=0x" << ph.p_filesz << std::dec << std::endl;
    }
  }

  std::cout << "Entry point: 0x" << std::hex << elf_header.e_entry << std::dec
            << std::endl;

  return true;
}

bool ELFLoader::load(std::vector<ELFSection> &sections, uint32_t &entry_point,
                     const std::string &filename) {
  sections.clear();
  entry_point = 0;
  return false;
}

} // namespace demu
