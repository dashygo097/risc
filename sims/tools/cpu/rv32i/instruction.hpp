#pragma once

#include <cstdint>
#include <string>

enum InstType { R_TYPE, I_TYPE, S_TYPE, B_TYPE, U_TYPE, J_TYPE, UNKNOWN };

class Instruction {
public:
  explicit Instruction(uint32_t raw);

  [[nodiscard]] InstType get_type() const noexcept;
  [[nodiscard]] std::string get_mnemonic() const noexcept;
  [[nodiscard]] std::string to_string() const;

  uint8_t get_opcode() const { return _opcode; }
  uint8_t get_rd() const { return _rd; }
  uint8_t get_rs1() const { return _rs1; }
  uint8_t get_rs2() const { return _rs2; }
  uint8_t get_funct3() const { return _funct3; }
  uint8_t get_funct7() const { return _funct7; }
  int32_t get_imm() const { return _imm; }

  static uint32_t add(uint8_t rd, uint8_t rs1, uint8_t rs2);
  static uint32_t addi(uint8_t rd, uint8_t rs1, int16_t imm);
  static uint32_t lw(uint8_t rd, uint8_t rs1, int16_t offset);
  static uint32_t sw(uint8_t rs2, uint8_t rs1, int16_t offset);
  static uint32_t beq(uint8_t rs1, uint8_t rs2, int16_t offset);
  static uint32_t jal(uint8_t rd, int32_t offset);

private:
  uint32_t _raw;
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
