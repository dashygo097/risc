#pragma once

#include "../../allocator.hh"
#include "./slave.hh"
#include <queue>
#include <string>

namespace demu::hal::axi {

class AXILiteMemory final : public AXILiteSlave {
public:
  explicit AXILiteMemory(addr_t base_addr, size_t size, size_t read_delay = 1,
                         size_t write_delay = 1)
      : AXILiteSlave("AXILite Memory", risc::DEVICE_TYPE_MEMORY, base_addr,
                     size),
        memory_allocator_(std::make_unique<MemoryAllocator>(base_addr, size)),
        read_delay_(read_delay), write_delay_(write_delay) {}

  ~AXILiteMemory() override = default;

  [[nodiscard]] addr_t base_address() const noexcept override {
    return memory_allocator_->base_address();
  }
  [[nodiscard]] size_t address_range() const noexcept override {
    return memory_allocator_->size();
  }
  [[nodiscard]] const char *name() const noexcept override {
    return "AXILite Memory";
  }

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
    return memory_allocator_->load_binary(filename, offset);
  }
  void read_delay(size_t cycles) noexcept { read_delay_ = cycles; }
  void write_delay(size_t cycles) noexcept { write_delay_ = cycles; }

private:
  std::unique_ptr<MemoryAllocator> memory_allocator_;
  size_t read_delay_;
  size_t write_delay_;

  struct WriteData {
    word_t data;
    byte_t strb;
  };
  struct WriteResponse {
    uint8_t resp;
    size_t delay;
  };
  struct ReadTransaction {
    addr_t addr;
    word_t data;
    bool processed;
    size_t delay;
  };

  std::queue<addr_t> _write_addr_queue;
  std::queue<WriteData> _write_data_queue;
  std::queue<WriteResponse> _write_resp_queue;
  std::queue<ReadTransaction> _read_queue;

  void process_writes();
  void process_reads();
  void update_delays();
};

} // namespace demu::hal::axi
