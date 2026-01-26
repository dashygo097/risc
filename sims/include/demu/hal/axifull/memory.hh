#pragma once
#include "../memory.hh"
#include "./slave.hh"
#include <memory>
#include <queue>

namespace demu::hal::axi {

class AXIFullMemory final : public AXIFullSlave {
public:
  AXIFullMemory(size_t size, addr_t base_addr = 0x80000000,
                size_t read_delay = 1, size_t write_delay = 1);

  ~AXIFullMemory() override = default;

  [[nodiscard]] addr_t base_address() const noexcept override {
    return _memory->base_address();
  }
  [[nodiscard]] size_t address_range() const noexcept override {
    return _memory->size();
  }

  void clock_tick() override;
  void reset() override;

  // AW channel
  void aw_valid(addr_t addr, uint16_t id, uint8_t len, uint8_t size,
                uint8_t burst, uint8_t lock, uint8_t cache, uint8_t prot,
                uint8_t qos, uint8_t region) override;
  bool aw_ready() const noexcept override;

  // W channel
  void w_valid(word_t data, byte_t strb, bool last, uint16_t id) override;
  bool w_ready() const noexcept override;

  // B channel
  bool b_valid() const noexcept override;
  void b_ready(bool ready) override;
  uint8_t b_resp() const noexcept override;
  uint16_t b_id() const noexcept override;

  // AR channel
  void ar_valid(addr_t addr, uint16_t id, uint8_t len, uint8_t size,
                uint8_t burst, uint8_t lock, uint8_t cache, uint8_t prot,
                uint8_t qos, uint8_t region) override;
  bool ar_ready() const noexcept override;

  // R channel
  bool r_valid() const noexcept override;
  void r_ready(bool ready) override;
  word_t r_data() const noexcept override;
  uint8_t r_resp() const noexcept override;
  bool r_last() const noexcept override;
  uint16_t r_id() const noexcept override;

  // Bypass Methods
  bool load_binary(const std::string &filename, addr_t offset = 0) {
    return _memory->load_binary(filename, offset);
  };
  void read_delay(size_t cycles) { _read_delay = cycles; };
  void write_delay(size_t cycles) { _write_delay = cycles; };

  [[nodiscard]] byte_t *get_ptr(addr_t offset = 0) {
    return _memory->get_ptr(offset);
  }
  [[nodiscard]] const byte_t *get_ptr(addr_t offset = 0) const {
    return _memory->get_ptr(offset);
  };

  [[nodiscard]] const char *name() const noexcept override {
    return "AXIFull Memory";
  }

private:
  struct WriteAddrTransaction {
    addr_t base_addr;
    uint16_t id;
    uint8_t len;   // Number of transfers = len + 1
    uint8_t size;  // Bytes per transfer = 2^size
    uint8_t burst; // 0=FIXED, 1=INCR, 2=WRAP
  };

  struct WriteDataTransaction {
    word_t data;
    byte_t strb;
    bool last;
    uint16_t id;
  };

  struct WriteRespTransaction {
    uint8_t resp;
    uint16_t id;
    size_t delay;
  };

  struct ReadAddrTransaction {
    addr_t base_addr;
    uint16_t id;
    uint8_t len;
    uint8_t size;
    uint8_t burst;
  };

  struct ReadDataTransaction {
    word_t data;
    uint8_t resp;
    bool last;
    uint16_t id;
    size_t delay;
    bool processed;
  };

  void process_writes();
  void process_reads();
  void update_delays();

  addr_t calculate_address(addr_t base, uint8_t beat, uint8_t len, uint8_t size,
                           uint8_t burst) const;

  std::unique_ptr<Memory> _memory;
  size_t _read_delay;
  size_t _write_delay;

  std::queue<WriteAddrTransaction> _write_addr_queue;
  std::queue<WriteDataTransaction> _write_data_queue;
  std::queue<WriteRespTransaction> _write_resp_queue;

  std::queue<ReadAddrTransaction> _read_addr_queue;
  std::queue<ReadDataTransaction> _read_data_queue;

  // Active write burst tracking
  bool _write_burst_active = false;
  WriteAddrTransaction _current_write_burst;
  uint8_t _write_beat_count = 0;
};

} // namespace demu::hal::axi
