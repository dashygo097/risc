#pragma once
#include "../../allocator.hh"
#include "../../peripheral/sram/sram.hh"
#include "./slave.hh"
#include <queue>

namespace demu::hal::axif {

class AXIFullSRAM final : public AXIFullSlave {
public:
  explicit AXIFullSRAM(const risc::DeviceDescriptor &desc)
      : AXIFullSlave(desc), sram_(std::make_unique<sram::SRAM>(desc)) {}
  ~AXIFullSRAM() override = default;

  void reset() override;
  void clock_tick() override;
  void dump(addr_t start, size_t size) const noexcept override;

  void aw_valid(bool valid, uint32_t id, addr_t addr, uint8_t len, uint8_t size,
                uint8_t burst) override {
    pin_awvalid = valid;
    pin_awid = id;
    pin_awaddr = addr;
    pin_awlen = len;
    pin_awsize = size;
    pin_awburst = burst;
  }
  void w_valid(bool valid, word_t data, byte_t strb, bool last) override {
    pin_wvalid = valid;
    pin_wdata = data;
    pin_wstrb = strb;
    pin_wlast = last;
  }
  void b_ready(bool ready) override { pin_bready = ready; }

  void ar_valid(bool valid, uint32_t id, addr_t addr, uint8_t len, uint8_t size,
                uint8_t burst) override {
    pin_arvalid = valid;
    pin_arid = id;
    pin_araddr = addr;
    pin_arlen = len;
    pin_arsize = size;
    pin_arburst = burst;
  }
  void r_ready(bool ready) override { pin_rready = ready; }

  auto aw_ready() const noexcept -> bool override { return true; }
  auto w_ready() const noexcept -> bool override { return true; }
  auto b_valid() const noexcept -> bool override {
    return !_write_resp_queue.empty();
  }
  auto b_resp() const noexcept -> uint8_t override {
    return b_valid() ? _write_resp_queue.front().resp : 0;
  }
  auto b_id() const noexcept -> uint32_t override {
    return b_valid() ? _write_resp_queue.front().id : 0;
  }

  auto ar_ready() const noexcept -> bool override { return true; }
  auto r_valid() const noexcept -> bool override {
    return !_read_data_queue.empty();
  }
  auto r_data() const noexcept -> word_t override {
    return r_valid() ? _read_data_queue.front().data : 0;
  }
  auto r_resp() const noexcept -> uint8_t override {
    return r_valid() ? _read_data_queue.front().resp : 0;
  }
  auto r_id() const noexcept -> uint32_t override {
    return r_valid() ? _read_data_queue.front().id : 0;
  }
  auto r_last() const noexcept -> bool override {
    return r_valid() ? _read_data_queue.front().last : false;
  }

  // Bypass
  [[nodiscard]] auto allocator() const noexcept -> MemoryAllocator * override {
    return sram_->allocator();
  }

private:
  std::unique_ptr<sram::SRAM> sram_;

  // Cached Pin States
  bool pin_awvalid{false};
  uint32_t pin_awid{0};
  addr_t pin_awaddr{0};
  uint8_t pin_awlen{0};
  uint8_t pin_awsize{0};
  uint8_t pin_awburst{0};

  bool pin_wvalid{false};
  word_t pin_wdata{0};
  byte_t pin_wstrb{0};
  bool pin_wlast{false};

  bool pin_bready{false};

  bool pin_arvalid{false};
  uint32_t pin_arid{0};
  addr_t pin_araddr{0};
  uint8_t pin_arlen{0};
  uint8_t pin_arsize{0};
  uint8_t pin_arburst{0};

  bool pin_rready{false};

  struct BurstTransaction {
    uint32_t id;
    addr_t addr;
    uint8_t len;
    uint8_t size;
    uint8_t burst;
    uint8_t beats_completed;
  };

  struct WriteData {
    word_t data;
    byte_t strb;
    bool last;
  };

  struct WriteResponse {
    uint32_t id;
    uint8_t resp;
  };

  struct ReadData {
    uint32_t id;
    word_t data;
    uint8_t resp;
    bool last;
  };

  std::queue<BurstTransaction> _write_req_queue;
  std::queue<WriteData> _write_data_queue;
  std::queue<WriteResponse> _write_resp_queue;

  std::queue<BurstTransaction> _read_req_queue;
  std::queue<ReadData> _read_data_queue;

  void process_writes();
  void process_reads();
  void calculate_next_address(BurstTransaction &req);
};

} // namespace demu::hal::axif
