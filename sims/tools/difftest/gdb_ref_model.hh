#pragma once

#include "demu/logger.hh"
#include "ref_model.hh"
#include <arpa/inet.h>
#include <iostream>
#include <sstream>
#include <string>
#include <sys/socket.h>
#include <unistd.h>

namespace demu::difftest {

class GdbRefModel final : public IRefModel {
private:
  int sock_ = -1;
  CPU_state state_{};

  auto checksum(const std::string &data) -> std::string {
    byte_t csum = 0;
    for (char c : data) {
      csum += static_cast<byte_t>(c);
    }
    char buf[4];
    snprintf(buf, sizeof(buf), "%02x", csum);
    return buf;
  }

  void send_packet(const std::string &data) {
    std::string packet = "$" + data + "#" + checksum(data);
    send(sock_, packet.c_str(), packet.length(), 0);
    char ack;
    recv(sock_, &ack, 1, 0);
    if (ack != '+') {
      DEMU_ERROR("GDB: Invalid ACK received: {}", ack);
    }
  }

  auto recv_packet() -> std::string {
    char c;
    std::string data;
    while (recv(sock_, &c, 1, 0) == 1 && c != '$') {
      ;
    }
    while (recv(sock_, &c, 1, 0) == 1 && c != '#') {
      data += c;
    }
    recv(sock_, &c, 1, 0);
    recv(sock_, &c, 1, 0);

    c = '+';
    send(sock_, &c, 1, 0);
    return data;
  }

  auto decode_hex_le(const std::string &hex, size_t offset) -> word_t {
    if (offset + 8 > hex.length()) {
      return 0;
    }
    word_t val = 0;
    for (int i = 0; i < 4; i++) {
      std::string byte_str = hex.substr(offset + i * 2, 2);
      val |=
          (static_cast<word_t>(std::stoul(byte_str, nullptr, 16)) << (i * 8));
    }
    return val;
  }

public:
  explicit GdbRefModel(int port = 1234) {
    sock_ = socket(AF_INET, SOCK_STREAM, 0);
    struct sockaddr_in serv_addr{};
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(port);
    inet_pton(AF_INET, "127.0.0.1", &serv_addr.sin_addr);

    DEMU_INFO("Connecting to QEMU GDB on localhost:{} ...", port);
    if (connect(sock_, reinterpret_cast<struct sockaddr *>(&serv_addr),
                sizeof(serv_addr)) < 0) {
      DEMU_ERROR("Connection failed! Is QEMU running with '-s -S'?");
      exit(1);
    }
    DEMU_INFO("Connected to QEMU successfully!");
  }

  ~GdbRefModel() override {
    if (sock_ >= 0) {
      close(sock_);
    }
  }

  auto init() -> bool override {
    send_packet("?");
    recv_packet();
    return true;
  }

  void sync_memory(addr_t addr, size_t size, const void *data) override {
    const auto *bytes = static_cast<const byte_t *>(data);
    std::stringstream ss;
    ss << "M" << std::hex << addr << "," << size << ":";
    for (size_t i = 0; i < size; i++) {
      char buf[3];
      snprintf(buf, sizeof(buf), "%02x", bytes[i]);
      ss << buf;
    }
    send_packet(ss.str());
    recv_packet();
  }

  void step(uint64_t n) override {
    for (uint64_t i = 0; i < n; ++i) {
      send_packet("s");
      recv_packet();
    }
  }

  void push_state() override {
    std::stringstream ss;
    ss << "G";
    auto encode_hex_le = [](word_t val) {
      char buf[9];
      snprintf(buf, sizeof(buf), "%02x%02x%02x%02x", val & 0xFF,
               (val >> 8) & 0xFF, (val >> 16) & 0xFF, (val >> 24) & 0xFF);
      return std::string(buf);
    };

    for (int i = 0; i < NUM_GPRS; i++) {
      ss << encode_hex_le(state_.gpr[i]);
    }
    ss << encode_hex_le(static_cast<word_t>(state_.pc));

    send_packet(ss.str());
    recv_packet(); // Wait for 'OK'
  }

  void pull_state() override {
    send_packet("g");
    std::string regs_hex = recv_packet();

    for (int i = 0; i < NUM_GPRS; i++) {
      state_.gpr[i] = decode_hex_le(regs_hex, i * 8);
    }
    state_.pc = static_cast<addr_t>(decode_hex_le(regs_hex, NUM_GPRS * 8));
  }

  [[nodiscard]] auto get_pc() const -> addr_t override { return state_.pc; }

  [[nodiscard]] auto get_reg(uint8_t idx) const -> word_t override {
    return state_.gpr[idx];
  }

  void set_pc(addr_t pc) override { state_.pc = pc; }

  void set_reg(uint8_t idx, word_t val) override { state_.gpr[idx] = val; }
};

inline auto create_ref_model(const std::string &path)
    -> std::unique_ptr<IRefModel> {
  if (path == "gdb") {
    return std::make_unique<GdbRefModel>(1234);
  }

  return nullptr;
}

} // namespace demu::difftest
