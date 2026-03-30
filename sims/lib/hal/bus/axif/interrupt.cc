#include "demu/hal/bus/axif/interrupt.hh"

#if defined(__ISA_RV32I__) || defined(__ISA_RV32IM__)

namespace demu::hal::axi {

void AXIFullCLINT::reset() {
  msip_ = 0;
  mtimecmp_ = 0xFFFFFFFFFFFFFFFFull;
  mtime_ = 0;

  write_req_queue = std::queue<BurstTransaction>();
  write_data_queue = std::queue<WriteData>();
  write_resp_queue = std::queue<WriteResponse>();
  read_req_queue = std::queue<BurstTransaction>();
  read_data_queue = std::queue<ReadData>();

  pin_awvalid = false;
  pin_wvalid = false;
  pin_bready = false;
  pin_arvalid = false;
  pin_rready = false;

  if (timer_line_)
    timer_line_->deassert_line();
  if (soft_line_)
    soft_line_->deassert_line();
}

void AXIFullCLINT::clock_tick() {
  // RTC
  mtime_++;

  if (timer_line_)
    timer_line_->set_level(mtime_ >= mtimecmp_);
  if (soft_line_)
    soft_line_->set_level((msip_ & 1) != 0);

  if (pin_awvalid && aw_ready()) {
    write_req_queue.push(
        {pin_awid, pin_awaddr, pin_awlen, pin_awsize, pin_awburst, 0});
  }
  if (pin_wvalid && w_ready()) {
    write_data_queue.push({pin_wdata, pin_wstrb, pin_wlast});
  }
  if (pin_bready && b_valid()) {
    write_resp_queue.pop();
  }
  if (pin_arvalid && ar_ready()) {
    read_req_queue.push(
        {pin_arid, pin_araddr, pin_arlen, pin_arsize, pin_arburst, 0});
  }
  if (pin_rready && r_valid()) {
    read_data_queue.pop();
  }

  process_writes();
  process_reads();
}

void AXIFullCLINT::calculate_next_address(BurstTransaction &req) {
  if (req.burst == 1 || req.burst == 2) {
    req.addr += (1u << req.size);
  }
}

auto AXIFullCLINT::read_register(addr_t offset) const noexcept -> word_t {
  switch (offset) {
  case 0x0000:
    return msip_;
  case 0x4000:
    return static_cast<uint32_t>(mtimecmp_ & 0xFFFFFFFF);
  case 0x4004:
    return static_cast<uint32_t>(mtimecmp_ >> 32);
  case 0xBFF8:
    return static_cast<uint32_t>(mtime_ & 0xFFFFFFFF);
  case 0xBFFC:
    return static_cast<uint32_t>(mtime_ >> 32);
  default:
    return 0;
  }
}

void AXIFullCLINT::write_register(addr_t offset, word_t data,
                                  byte_t strb) noexcept {
  uint32_t mask = 0;
  for (int i = 0; i < 4; ++i) {
    if (strb & (1u << i)) {
      mask |= (0xFF << (i * 8));
    }
  }

  auto apply_mask = [&](uint32_t old_val) -> uint32_t {
    return (old_val & ~mask) | (data & mask);
  };

  switch (offset) {
  case 0x0000:
    msip_ = apply_mask(msip_) & 1;
    break;
  case 0x4000: {
    uint32_t lo = apply_mask(static_cast<uint32_t>(mtimecmp_));
    mtimecmp_ = (mtimecmp_ & 0xFFFFFFFF00000000ull) | lo;
    break;
  }
  case 0x4004: {
    uint32_t hi = apply_mask(static_cast<uint32_t>(mtimecmp_ >> 32));
    mtimecmp_ = (static_cast<uint64_t>(hi) << 32) | (mtimecmp_ & 0xFFFFFFFF);
    break;
  }
  case 0xBFF8: {
    uint32_t lo = apply_mask(static_cast<uint32_t>(mtime_));
    mtime_ = (mtime_ & 0xFFFFFFFF00000000ull) | lo;
    break;
  }
  case 0xBFFC: {
    uint32_t hi = apply_mask(static_cast<uint32_t>(mtime_ >> 32));
    mtime_ = (static_cast<uint64_t>(hi) << 32) | (mtime_ & 0xFFFFFFFF);
    break;
  }
  }
}

void AXIFullCLINT::process_writes() {
  if (write_req_queue.empty() || write_data_queue.empty()) {
    return;
  }

  BurstTransaction &req = write_req_queue.front();
  const WriteData wdata = write_data_queue.front();
  write_data_queue.pop();

  const bool valid = owns_address(req.addr);

  if (valid) {
    addr_t offset = to_offset(req.addr);
    write_register(offset, wdata.data, wdata.strb);
  }

  req.beats_completed++;
  calculate_next_address(req);

  if (wdata.last || req.beats_completed > req.len) {
    write_resp_queue.push(
        {req.id,
         static_cast<uint8_t>(valid ? 0 : 2)}); // OKAY (0) or SLVERR (2)
    write_req_queue.pop();
  }
}

void AXIFullCLINT::process_reads() {
  if (read_req_queue.empty() || read_data_queue.size() >= 16) {
    return;
  }

  BurstTransaction &req = read_req_queue.front();
  const bool valid = owns_address(req.addr);

  word_t data = 0;
  if (valid) {
    addr_t offset = to_offset(req.addr);
    data = read_register(offset);
  }

  const bool last = (req.beats_completed == req.len);
  read_data_queue.push(
      {req.id, data, static_cast<uint8_t>(valid ? 0 : 2), last});

  req.beats_completed++;
  calculate_next_address(req);

  if (last) {
    read_req_queue.pop();
  }
}

} // namespace demu::hal::axi

#endif
