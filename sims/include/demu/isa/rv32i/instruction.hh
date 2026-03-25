#pragma once

#include "./typedefs.hh"
#include <cstdint>
#include <string>

#define EBREAK 0x00100073
#define BUBBLE 0x00000013

namespace demu::isa {
enum InstType {
  R_TYPE,
  I_TYPE,
  S_TYPE,
  B_TYPE,
  U_TYPE,
  J_TYPE,
  SYSTEM,
  UNKNOWN
};

class Instruction {
public:
  explicit Instruction(instr_t raw);

  [[nodiscard]] InstType type() const noexcept;
  [[nodiscard]] std::string mnemonic() const noexcept;
  [[nodiscard]] std::string to_string() const;

  [[nodiscard]] uint8_t opcode() const noexcept { return opcode_; }
  [[nodiscard]] uint8_t rd() const noexcept { return rd_; }
  [[nodiscard]] uint8_t rs1() const noexcept { return rs1_; }
  [[nodiscard]] uint8_t rs2() const noexcept { return rs2_; }
  [[nodiscard]] uint8_t funct3() const noexcept { return funct3_; }
  [[nodiscard]] uint8_t funct7() const noexcept { return funct7_; }
  [[nodiscard]] int32_t imm() const noexcept { return imm_; }

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
  [[nodiscard]] int32_t decode_i_imm() const noexcept;
  [[nodiscard]] int32_t decode_s_imm() const noexcept;
  [[nodiscard]] int32_t decode_b_imm() const noexcept;
  [[nodiscard]] int32_t decode_u_imm() const noexcept;
  [[nodiscard]] int32_t decode_j_imm() const noexcept;
};

} // namespace demu::isa
