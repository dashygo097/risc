#include "elf_loader.h"
#include "memory.h"
#include <cstring>
#include <fstream>
#include <iostream>

// Simple ELF32 header structures
struct ELF32_Header {
  uint8_t e_ident[16];
  uint16_t e_type;
  uint16_t e_machine;
  uint32_t e_version;
  uint32_t e_entry;
  uint32_t e_phoff;
  uint32_t e_shoff;
  uint32_t e_flags;
  uint16_t e_ehsize;
  uint16_t e_phentsize;
  uint16_t e_phnum;
  uint16_t e_shentsize;
  uint16_t e_shnum;
  uint16_t e_shstrndx;
};

struct ELF32_ProgramHeader {
  uint32_t p_type;
  uint32_t p_offset;
  uint32_t p_vaddr;
  uint32_t p_paddr;
  uint32_t p_filesz;
  uint32_t p_memsz;
  uint32_t p_flags;
  uint32_t p_align;
};

bool ELFLoader::is_elf(const std::string &filename) {
  std::ifstream file(filename, std::ios::binary);
  if (!file.is_open())
    return false;

  uint8_t magic[4];
  file.read(reinterpret_cast<char *>(magic), 4);

  return (magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' &&
          magic[3] == 'F');
}

bool ELFLoader::load(const std::string &filename, Memory &mem) {
  if (!is_elf(filename)) {
    std::cerr << "Not a valid ELF file" << std::endl;
    return false;
  }

  std::ifstream file(filename, std::ios::binary);
  if (!file.is_open()) {
    std::cerr << "Failed to open ELF file: " << filename << std::endl;
    return false;
  }

  // Read ELF header
  ELF32_Header elf_header;
  file.read(reinterpret_cast<char *>(&elf_header), sizeof(elf_header));

  // Check if it's RISC-V
  if (elf_header.e_machine != 0xF3) { // EM_RISCV = 243 (0xF3)
    std::cerr << "Not a RISC-V ELF file" << std::endl;
    return false;
  }

  // Load program headers
  file.seekg(elf_header.e_phoff);

  for (int i = 0; i < elf_header.e_phnum; i++) {
    ELF32_ProgramHeader ph;
    file.read(reinterpret_cast<char *>(&ph), sizeof(ph));

    // PT_LOAD = 1
    if (ph.p_type == 1 && ph.p_filesz > 0) {
      std::vector<uint8_t> data(ph.p_filesz);
      file.seekg(ph.p_offset);
      file.read(reinterpret_cast<char *>(data.data()), ph.p_filesz);

      // Write to memory
      for (size_t j = 0; j < data.size(); j++) {
        mem.write8(ph.p_paddr + j, data[j]);
      }

      std::cout << "Loaded segment: addr=0x" << std::hex << ph.p_paddr
                << " size=0x" << ph.p_filesz << std::dec << std::endl;
    }
  }

  std::cout << "Entry point: 0x" << std::hex << elf_header.e_entry << std::dec
            << std::endl;

  return true;
}

bool ELFLoader::load(const std::string &filename,
                     std::vector<Section> &sections, uint32_t &entry_point) {
  // Simplified version - full implementation would parse section headers
  sections.clear();
  entry_point = 0;
  return false;
}
