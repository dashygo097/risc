#pragma once

#include "../../peripheral/uart/uart.hh"
#include "./slave.hh"

#include <cstdint>
#include <functional>
#include <memory>
#include <queue>

namespace demu::hal::axi {

using namespace isa;

class AXILiteUart final : public AXILiteSlave {
public:
  explicit AXILiteUart(addr_t base_addr, size_t size, size_t tx_fifo_depth = 16,
                       size_t rx_fifo_depth = 16, size_t read_delay = 0,
                       size_t write_delay = 0);

  ~AXILiteUart() override = default;

  AXILiteUart(const AXILiteUart &) = delete;
  AXILiteUart &operator=(const AXILiteUart &) = delete;
  AXILiteUart(AXILiteUart &&) = default;
  AXILiteUart &operator=(AXILiteUart &&) = default;

  [[nodiscard]] addr_t base_address() const noexcept override {
    return uart_->base_address();
  }
  [[nodiscard]] size_t address_range() const noexcept override {
    return uart_->address_range();
  }
  [[nodiscard]] const char *name() const noexcept override {
    return "AXILite UART";
  }

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

  bool rx_push(uint8_t byte) { return uart_->rx_push(byte); }

  void set_tx_callback(std::function<void(uint8_t)> cb) {
    uart_->set_tx_callback(std::move(cb));
  }

  void set_echo_stdout(bool enable) noexcept { uart_->set_echo_stdout(enable); }

  void read_delay(size_t cycles) noexcept { read_delay_ = cycles; }
  void write_delay(size_t cycles) noexcept { write_delay_ = cycles; }

  void dump(addr_t start, size_t size) const noexcept override;

private:
  // components
  std::unique_ptr<uart::Uart> uart_;

  size_t read_delay_;
  size_t write_delay_;

  struct WriteData {
    word_t data;
    byte_t strb;
  };

  struct WriteResponse {
    uint8_t resp; ///< OKAY = 0, SLVERR = 2
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
