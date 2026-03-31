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
  void aw_valid(addr_t addr) override { _write_addr_queue.push(addr); };
  auto aw_ready() const noexcept -> bool override { return true; };

  // W
  void w_valid(word_t data, byte_t strb) override {
    _write_data_queue.push({data, strb});
  }
  auto w_ready() const noexcept -> bool override { return true; }

  // B
  auto b_valid() const noexcept -> bool override {
    return !_write_resp_queue.empty();
  }
  void b_ready(bool ready) override {
    if (ready && !_write_resp_queue.empty()) {
      _write_resp_queue.pop();
    }
  }
  auto b_resp() const noexcept -> uint8_t override {
    return _write_resp_queue.empty() ? 0u : _write_resp_queue.front().resp;
  }

  // AR
  void ar_valid(addr_t addr) override { _read_queue.push({addr, 0u, false}); }
  auto ar_ready() const noexcept -> bool override { return true; }

  // R
  auto r_valid() const noexcept -> bool override {
    return !_read_queue.empty() && _read_queue.front().processed;
  }
  void r_ready(bool ready) override {
    if (ready && r_valid()) {
      _read_queue.pop();
    }
  }
  auto r_data() const noexcept -> word_t override {
    return _read_queue.empty() ? 0u : _read_queue.front().data;
  }
  auto r_resp() const noexcept -> uint8_t override { return 0u; }

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
