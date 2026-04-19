#pragma once

#include "../../allocator.hh"
#include "../../interrupt.hh"
#include "./slave.hh"
#include <memory>
#include <queue>

#if defined(__ISA_RV32I__) || defined(__ISA_RV32IM__)

namespace demu::hal::axil {

enum ClintRegisters : addr_t {
  CLINT_MSIP = 0x0000,
  CLINT_MTIMECMP_LO = 0x4000,
  CLINT_MTIMECMP_HI = 0x4004,
  CLINT_MTIME_LO = 0xBFF8,
  CLINT_MTIME_HI = 0x8FFC
};

constexpr const uint64_t TICK_MS_DIVIDER = 1000;
constexpr const uint64_t TICK_US_DIVIDER = TICK_MS_DIVIDER * 1000;
constexpr const uint64_t TICK_NS_DIVIDER = TICK_US_DIVIDER * 1000;

class AXILiteCLINT final : public AXILiteSlave {
public:
  explicit AXILiteCLINT(const risc::DeviceDescriptor &desc, uint64_t freq,
                        InterruptLine *timer_line = nullptr,
                        InterruptLine *soft_line = nullptr)
      : AXILiteSlave(desc),
        allocator_(std::make_unique<MemoryAllocator>(desc.base(), desc.size())),
        freq_(freq), timer_line_(timer_line), soft_line_(soft_line) {}

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
    return allocator_.get();
  }

private:
  std::unique_ptr<MemoryAllocator> allocator_;
  uint64_t freq_;
  InterruptLine *timer_line_;
  InterruptLine *soft_line_;

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

} // namespace demu::hal::axil

#endif // defined(__ISA_RV32I__) || defined(__ISA_RV32IM__)
