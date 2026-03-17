#include "demu/hal/bus/axil/memory.hh"
#include <iomanip>
#include <sstream>

namespace demu::hal::axi {

void AXILiteMemory::reset() {
  memory_->clear();
  _write_addr_queue = {};
  _write_data_queue = {};
  _write_resp_queue = {};
  _read_queue = {};
}

void AXILiteMemory::clock_tick() {
  process_writes();
  process_reads();
}

void AXILiteMemory::process_writes() {
  if (_write_addr_queue.empty() || _write_data_queue.empty())
    return;

  const addr_t addr = _write_addr_queue.front();
  _write_addr_queue.pop();
  const WriteData wdata = _write_data_queue.front();
  _write_data_queue.pop();

  const bool valid =
      memory_->is_valid_addr(addr) && memory_->is_valid_addr(addr + 3);
  if (valid) {
    for (int i = 0; i < 4; ++i)
      if (wdata.strb & (1u << i))
        memory_->write_byte(
            addr + i, static_cast<byte_t>((wdata.data >> (i * 8)) & 0xFF));
  }

  _write_resp_queue.push({valid ? uint8_t(0) : uint8_t(2)});
}

void AXILiteMemory::process_reads() {
  if (_read_queue.empty() || _read_queue.front().processed)
    return;

  ReadTransaction &rt = _read_queue.front();
  const bool valid =
      memory_->is_valid_addr(rt.addr) && memory_->is_valid_addr(rt.addr + 3);
  rt.data = valid ? memory_->read_word(rt.addr) : 0u;
  rt.processed = true;
}

// AW
void AXILiteMemory::aw_valid(addr_t addr) { _write_addr_queue.push(addr); }
bool AXILiteMemory::aw_ready() const noexcept { return true; }

// W
void AXILiteMemory::w_valid(word_t data, byte_t strb) {
  _write_data_queue.push({data, strb});
}
bool AXILiteMemory::w_ready() const noexcept { return true; }

// B
bool AXILiteMemory::b_valid() const noexcept {
  return !_write_resp_queue.empty();
}
void AXILiteMemory::b_ready(bool ready) {
  if (ready && !_write_resp_queue.empty())
    _write_resp_queue.pop();
}
uint8_t AXILiteMemory::b_resp() const noexcept {
  return _write_resp_queue.empty() ? 0u : _write_resp_queue.front().resp;
}

// AR
void AXILiteMemory::ar_valid(addr_t addr) {
  _read_queue.push({addr, 0u, false});
}
bool AXILiteMemory::ar_ready() const noexcept { return true; }

// R
bool AXILiteMemory::r_valid() const noexcept {
  return !_read_queue.empty() && _read_queue.front().processed;
}
void AXILiteMemory::r_ready(bool ready) {
  if (ready && r_valid())
    _read_queue.pop();
}
word_t AXILiteMemory::r_data() const noexcept {
  return _read_queue.empty() ? 0u : _read_queue.front().data;
}
uint8_t AXILiteMemory::r_resp() const noexcept { return 0u; }

void AXILiteMemory::dump(addr_t start, size_t size) const noexcept {
  if (!owns_address(start)) {
    HAL_WARN("AXILiteMemory dump: 0x{:08X} not owned",
             static_cast<uint64_t>(start));
    return;
  }
  const size_t clamped = std::min(
      size, static_cast<size_t>(base_address() + address_range() - start));

  HAL_INFO("Memory dump [0x{:08X} - 0x{:08X}]:", static_cast<uint64_t>(start),
           static_cast<uint64_t>(start + clamped));

  const byte_t *ptr = memory_->get_ptr(start);
  if (!ptr)
    return;

  for (size_t i = 0; i < clamped; i += 16) {
    std::stringstream ss;
    ss << std::hex << std::setw(8) << std::setfill('0') << (start + i) << ": ";
    for (size_t j = 0; j < 16; ++j) {
      if (i + j < clamped)
        ss << std::hex << std::setw(2) << std::setfill('0')
           << static_cast<int>(ptr[i + j]) << ' ';
      else
        ss << "   ";
      if (j == 7)
        ss << ' ';
    }
    ss << " |";
    for (size_t j = 0; j < 16 && (i + j) < clamped; ++j) {
      const byte_t c = ptr[i + j];
      ss << static_cast<char>((c >= 32 && c < 127) ? c : '.');
    }
    ss << '|';
    HAL_INFO("{}", ss.str());
  }
}

} // namespace demu::hal::axi
