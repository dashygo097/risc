#ifndef INSTRUCTION_H
#define INSTRUCTION_H

#include <cstdint>
#include <string>

class Instruction {
public:
  // Instruction types
  enum Type { R_TYPE, I_TYPE, S_TYPE, B_TYPE, U_TYPE, J_TYPE, UNKNOWN };

  explicit Instruction(uint32_t raw);

  // Decode
  Type get_type() const;
  std::string get_mnemonic() const;
  std::string to_string() const;

  // Fields
  uint8_t get_opcode() const { return opcode_; }
  uint8_t get_rd() const { return rd_; }
  uint8_t get_rs1() const { return rs1_; }
  uint8_t get_rs2() const { return rs2_; }
  uint8_t get_funct3() const { return funct3_; }
  uint8_t get_funct7() const { return funct7_; }
  int32_t get_imm() const { return imm_; }

  // Static encoders (for testing)
  static uint32_t encode_add(uint8_t rd, uint8_t rs1, uint8_t rs2);
  static uint32_t encode_addi(uint8_t rd, uint8_t rs1, int16_t imm);
  static uint32_t encode_lw(uint8_t rd, uint8_t rs1, int16_t offset);
  static uint32_t encode_sw(uint8_t rs2, uint8_t rs1, int16_t offset);
  static uint32_t encode_beq(uint8_t rs1, uint8_t rs2, int16_t offset);
  static uint32_t encode_jal(uint8_t rd, int32_t offset);

private:
  uint32_t raw_;
  uint8_t opcode_;
  uint8_t rd_;
  uint8_t rs1_;
  uint8_t rs2_;
  uint8_t funct3_;
  uint8_t funct7_;
  int32_t imm_;

  void decode();
  int32_t decode_i_imm() const;
  int32_t decode_s_imm() const;
  int32_t decode_b_imm() const;
  int32_t decode_u_imm() const;
  int32_t decode_j_imm() const;
};

#endif // INSTRUCTION_H
