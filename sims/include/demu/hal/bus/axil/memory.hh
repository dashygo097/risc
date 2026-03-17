#pragma once

#include "../../allocator.hh"
#include "./slave.hh"
#include <queue>
#include <string>

namespace demu::hal::axi {

class AXILiteMemory final : public AXILiteSlave {
public:
  explicit AXILiteMemory(const risc::DeviceDescriptor &desc)
      : AXILiteSlave(desc), memory_(std::make_unique<MemoryAllocator>(
                                static_cast<addr_t>(desc.base()),
                                static_cast<size_t>(desc.size()))) {}

  ~AXILiteMemory() override = default;

  void reset() override;
  void clock_tick() override;
  void dump(addr_t start, size_t size) const noexcept override;

  // AW
  void aw_valid(addr_t addr) override;
  bool aw_ready() const noexcept override;
  // W
  void w_valid(word_t data, byte_t strb) override;
  bool w_ready() const noexcept override;
  // B
  bool b_valid() const noexcept override;
  void b_ready(bool ready) override;
  uint8_t b_resp() const noexcept override;
  // AR
  void ar_valid(addr_t addr) override;
  bool ar_ready() const noexcept override;
  // R
  bool r_valid() const noexcept override;
  void r_ready(bool ready) override;
  word_t r_data() const noexcept override;
  uint8_t r_resp() const noexcept override;

  // Bypass
  bool load_binary(const std::string &filename, addr_t offset = 0) {
    return memory_->load_binary(filename, offset);
  }

private:
  std::unique_ptr<MemoryAllocator> memory_;

  struct WriteData {
    word_t data;
    byte_t strb;
  };
  struct WriteResponse {
    uint8_t resp;
  };
  struct ReadTransaction {
    addr_t addr;
    word_t data;
    bool processed;
  };

  std::queue<addr_t> _write_addr_queue;
  std::queue<WriteData> _write_data_queue;
  std::queue<WriteResponse> _write_resp_queue;
  std::queue<ReadTransaction> _read_queue;

  void process_writes();
  void process_reads();
};

} // namespace demu::hal::axi
