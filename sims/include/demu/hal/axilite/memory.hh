#pragma once

#include "../memory.hh"
#include "./slave.hh"
#include <cstddef>
#include <cstdint>
#include <queue>
#include <string>

namespace demu::hal::axi {

class AXILiteMemory final : public AXILiteSlave {
public:
  explicit AXILiteMemory(size_t size, addr_t base_addr = 0x0,
                         size_t read_delay = 1, size_t write_delay = 1);

  ~AXILiteMemory() override = default;

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
  bool load_binary(const std::string &filename, addr_t offset = 0) {
    return _memory->load_binary(filename, offset);
  };
  void read_delay(size_t cycles) { _read_delay = cycles; };
  void write_delay(size_t cycles) { _write_delay = cycles; };

  [[nodiscard]] const char *name() const noexcept override {
    return "AXILite Memory";
  }
  [[nodiscard]] addr_t base_address() const noexcept override {
    return _memory->base_address();
  }
  [[nodiscard]] size_t size() const noexcept override {
    return _memory->size();
  }
  [[nodiscard]] byte_t *get_ptr(addr_t offset = 0) {
    return _memory->get_ptr(offset);
  }
  [[nodiscard]] const byte_t *get_ptr(addr_t offset = 0) const {
    return _memory->get_ptr(offset);
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
  std::unique_ptr<Memory> _memory;
  size_t _read_delay;
  size_t _write_delay;

  // Transaction queues for pipelined operation
  std::queue<addr_t> _write_addr_queue;
  std::queue<WriteData> _write_data_queue;
  std::queue<WriteResponse> _write_resp_queue;
  std::queue<ReadTransaction> _read_queue;
};

} // namespace demu::hal::axi
