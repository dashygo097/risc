#pragma once

#include "./typedefs.hh"
#include <cstdint>
#include <string>

namespace demu::isa {
enum InstrType {
  R_TYPE,
  I_TYPE,
  S_TYPE,
  B_TYPE,
  U_TYPE,
  J_TYPE,
  SYSTEM,
  UNKNOWN
};

enum SystemInstr {
  EBREAK = 0x00100073,
  BUBBLE = 0x00000013,
  URET = 0x00200073,
  SRET = 0x20200073,
  MRET = 0x30200073,
};

class Instruction {
public:
  explicit Instruction(instr_t raw);

  [[nodiscard]] auto type() const noexcept -> InstrType;
  [[nodiscard]] auto mnemonic() const noexcept -> std::string;
  [[nodiscard]] auto to_string() const -> std::string;

  [[nodiscard]] auto opcode() const noexcept -> uint8_t { return opcode_; }
  [[nodiscard]] auto rd() const noexcept -> uint8_t { return rd_; }
  [[nodiscard]] auto rs1() const noexcept -> uint8_t { return rs1_; }
  [[nodiscard]] auto rs2() const noexcept -> uint8_t { return rs2_; }
  [[nodiscard]] auto funct3() const noexcept -> uint8_t { return funct3_; }
  [[nodiscard]] auto funct7() const noexcept -> uint8_t { return funct7_; }
  [[nodiscard]] auto imm() const noexcept -> int32_t { return imm_; }

private:
  instr_t raw_;
  uint8_t opcode_;
  uint8_t rd_;
  uint8_t rs1_;
  uint8_t rs2_;
  uint8_t funct3_;
  uint8_t funct7_;
  int32_t imm_;

  void decode();
  [[nodiscard]] auto decode_i_imm() const noexcept -> int32_t;
  [[nodiscard]] auto decode_s_imm() const noexcept -> int32_t;
  [[nodiscard]] auto decode_b_imm() const noexcept -> int32_t;
  [[nodiscard]] auto decode_u_imm() const noexcept -> int32_t;
  [[nodiscard]] auto decode_j_imm() const noexcept -> int32_t;
};

} // namespace demu::isa
