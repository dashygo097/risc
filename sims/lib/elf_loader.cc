#include "demu/elf_loader.hh"
#include "demu/logger.hh"
#include <cstring>
#include <fstream>

namespace demu {

auto ELFLoader::is_elf(const std::string &filename) -> bool {
  std::ifstream file(filename, std::ios::binary);
  if (!file.is_open()) {
    return false;
}

  uint8_t magic[4];
  file.read(reinterpret_cast<char *>(magic), 4);

  return (magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' &&
          magic[3] == 'F');
}

auto ELFLoader::load(hal::MemoryAllocator &mem, const std::string &filename) -> bool {
  if (!is_elf(filename)) {
    DEMU_ERROR("Not a valid ELF file: {}", filename);
    return false;
  }

  std::ifstream file(filename, std::ios::binary);
  if (!file.is_open()) {
    DEMU_ERROR("Failed to open ELF file: {}", filename);
    return false;
  }

  ELF32_Header elf_header;
  file.read(reinterpret_cast<char *>(&elf_header), sizeof(elf_header));

  if (elf_header.e_machine != 0xF3) {
    DEMU_ERROR("Not a RISC-V ELF file (e_machine=0x{:04x})",
               elf_header.e_machine);
    return false;
  }

  file.seekg(elf_header.e_phoff);

  for (int i = 0; i < elf_header.e_phnum; i++) {
    ELF32_ProgramHeader ph;
    file.read(reinterpret_cast<char *>(&ph), sizeof(ph));

    if (ph.p_type == PT_LOAD && ph.p_filesz > 0) {
      std::vector<uint8_t> data(ph.p_filesz);
      auto saved_pos = file.tellg();
      file.seekg(ph.p_offset);
      file.read(reinterpret_cast<char *>(data.data()), ph.p_filesz);
      file.seekg(saved_pos);

      for (size_t j = 0; j < data.size(); j++) {
        mem.write_byte(ph.p_paddr + j, data[j]);
      }

      DEMU_INFO("Loaded segment: addr=0x{:08x} size=0x{:x}", ph.p_paddr,
                ph.p_filesz);
    }
  }

  DEMU_INFO("Entry point: 0x{:08x}", elf_header.e_entry);
  return true;
}

auto ELFLoader::load(std::vector<ELFSection> &sections, uint32_t &entry_point,
                     const std::string &filename) -> bool {
  sections.clear();
  entry_point = 0;

  if (!is_elf(filename)) {
    DEMU_ERROR("Not a valid ELF file: {}", filename);
    return false;
  }

  std::ifstream file(filename, std::ios::binary);
  if (!file.is_open()) {
    DEMU_ERROR("Failed to open ELF file: {}", filename);
    return false;
  }

  ELF32_Header elf_header;
  file.read(reinterpret_cast<char *>(&elf_header), sizeof(elf_header));

  if (elf_header.e_machine != 0xF3) {
    DEMU_ERROR("Not a RISC-V ELF file (e_machine=0x{:04x})",
               elf_header.e_machine);
    return false;
  }

  entry_point = elf_header.e_entry;

  file.seekg(elf_header.e_phoff);

  for (uint16_t i = 0; i < elf_header.e_phnum; i++) {
    ELF32_ProgramHeader ph;
    auto ph_pos =
        static_cast<std::streamoff>(elf_header.e_phoff + i * sizeof(ph));
    file.seekg(ph_pos);
    file.read(reinterpret_cast<char *>(&ph), sizeof(ph));

    if (ph.p_type != PT_LOAD) {
      continue;
}

    ELFSection section;
    section.addr = ph.p_paddr;
    section.size = ph.p_memsz;

    if (ph.p_filesz > 0) {
      section.data.resize(ph.p_filesz);
      file.seekg(ph.p_offset);
      file.read(reinterpret_cast<char *>(section.data.data()), ph.p_filesz);
    }

    if (ph.p_flags & 0x1) { // PF_X
      section.name = fmt::format(".text@0x{:08x}", ph.p_paddr);
    } else if (ph.p_flags & 0x2) { // PF_W
      section.name = fmt::format(".data@0x{:08x}", ph.p_paddr);
    } else {
      section.name = fmt::format(".rodata@0x{:08x}", ph.p_paddr);
}

    DEMU_INFO("ELF segment: {} addr=0x{:08x} filesz=0x{:x} memsz=0x{:x}",
              section.name, ph.p_paddr, ph.p_filesz, ph.p_memsz);

    sections.push_back(std::move(section));
  }

  if (sections.empty()) {
    DEMU_ERROR("No loadable segments found in ELF: {}", filename);
    return false;
  }

  DEMU_INFO("Parsed {} loadable segments, entry=0x{:08x}", sections.size(),
            entry_point);
  return true;
}

} // namespace demu
