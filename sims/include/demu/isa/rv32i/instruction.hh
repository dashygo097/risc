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

  [[nodiscard]] uint8_t opcode() const noexcept { return _opcode; }
  [[nodiscard]] uint8_t rd() const noexcept { return _rd; }
  [[nodiscard]] uint8_t rs1() const noexcept { return _rs1; }
  [[nodiscard]] uint8_t rs2() const noexcept { return _rs2; }
  [[nodiscard]] uint8_t funct3() const noexcept { return _funct3; }
  [[nodiscard]] uint8_t funct7() const noexcept { return _funct7; }
  [[nodiscard]] int32_t imm() const noexcept { return _imm; }

private:
  instr_t _raw;
  uint8_t _opcode;
  uint8_t _rd;
  uint8_t _rs1;
  uint8_t _rs2;
  uint8_t _funct3;
  uint8_t _funct7;
  int32_t _imm;

  void decode();
  [[nodiscard]] int32_t decode_i_imm() const noexcept;
  [[nodiscard]] int32_t decode_s_imm() const noexcept;
  [[nodiscard]] int32_t decode_b_imm() const noexcept;
  [[nodiscard]] int32_t decode_u_imm() const noexcept;
  [[nodiscard]] int32_t decode_j_imm() const noexcept;
};

} // namespace demu::isa
