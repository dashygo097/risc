#pragma once

#include "../../allocator.hh"
#include "../../peripheral/sram/sram.hh"
#include "./slave.hh"
#include <queue>
#include <string>

namespace demu::hal::axi {

class AXILiteSRAM final : public AXILiteSlave {
public:
  explicit AXILiteSRAM(const risc::DeviceDescriptor &desc)
      : AXILiteSlave(desc), sram_(std::make_unique<sram::SRAM>(desc)) {}

  ~AXILiteSRAM() override = default;

  void reset() override;
  void clock_tick() override;
  void dump(addr_t start, size_t size) const noexcept override;

  // AW
  void aw_valid(addr_t addr) override;
  auto aw_ready() const noexcept -> bool override;
  // W
  void w_valid(word_t data, byte_t strb) override;
  auto w_ready() const noexcept -> bool override;
  // B
  auto b_valid() const noexcept -> bool override;
  void b_ready(bool ready) override;
  auto b_resp() const noexcept -> uint8_t override;
  // AR
  void ar_valid(addr_t addr) override;
  auto ar_ready() const noexcept -> bool override;
  // R
  auto r_valid() const noexcept -> bool override;
  void r_ready(bool ready) override;
  auto r_data() const noexcept -> word_t override;
  auto r_resp() const noexcept -> uint8_t override;

  // Bypass
  [[nodiscard]] auto allocator() const noexcept -> MemoryAllocator * override {
    return sram_->allocator();
  }

private:
  std::unique_ptr<sram::SRAM> sram_;

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
