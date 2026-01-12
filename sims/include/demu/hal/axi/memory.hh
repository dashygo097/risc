#pragma once

#include "../memory.hh"
#include "./slave.hh"
#include <cstddef>
#include <cstdint>
#include <queue>
#include <string>
#include <vector>

namespace demu::hal {

class AXIMemory final : public AXISlave {
public:
  explicit AXIMemory(size_t size, addr_t base_addr = 0x0, size_t read_delay = 1,
                     size_t write_delay = 1);

  ~AXIMemory() override = default;

  void reset() override;
  void clock_tick() override;

  // AW
  void aw_valid(addr_t addr) override;
  [[nodiscard]] bool aw_ready() const noexcept override;

  // W
  void w_valid(word_t data, byte_t strb) override;
  [[nodiscard]] bool w_ready() const noexcept override;

  // B
  [[nodiscard]] bool b_valid() const noexcept override;
  void b_ready(bool ready) override;
  [[nodiscard]] uint8_t b_resp() const noexcept override;

  // AR
  void ar_valid(addr_t addr) override;
  [[nodiscard]] bool ar_ready() const noexcept override;

  // R
  [[nodiscard]] bool r_valid() const noexcept override;
  void r_ready(bool ready) override;
  [[nodiscard]] word_t r_data() const noexcept override;
  [[nodiscard]] uint8_t r_resp() const noexcept override;

  // Bypass Methods
  [[nodiscard]] word_t read_word(addr_t addr) const noexcept;
  void write_word(addr_t addr, word_t data);

  bool load_binary(const std::string &filename, addr_t offset = 0);

  [[nodiscard]] const char *name() const noexcept override { return "AXI RAM"; }
  [[nodiscard]] addr_t base_address() const noexcept override {
    return base_addr_;
  }
  [[nodiscard]] size_t address_range() const noexcept override {
    return addr_range_;
  }
  void read_delay(size_t cycles) { read_delay_cycles_ = cycles; };

  void write_delay(size_t cycles) { write_delay_cycles_ = cycles; };

  [[nodiscard]] byte_t *get_ptr(addr_t offset = 0) {
    if (offset >= memory_.size()) {
      return nullptr;
    }
    return memory_.data() + offset;
  }
  [[nodiscard]] const byte_t *get_ptr(addr_t offset = 0) const {
    if (offset >= memory_.size()) {
      return nullptr;
    }
    return memory_.data() + offset;
  };

private:
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

  // Helper Methods
  void process_writes();
  void process_reads();
  void update_delays();

  // Components
  std::vector<byte_t> memory_;
  addr_t base_addr_;
  size_t addr_range_;
  size_t read_delay_cycles_;
  size_t write_delay_cycles_;

  // Transaction queues for pipelined operation
  std::queue<addr_t> write_addr_queue_;
  std::queue<WriteData> write_data_queue_;
  std::queue<WriteResponse> write_resp_queue_;
  std::queue<ReadTransaction> read_queue_;
};

} // namespace demu::hal
