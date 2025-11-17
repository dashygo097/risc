#include "instruction.hh"
#include <iomanip>
#include <sstream>

Instruction::Instruction(uint32_t raw) : raw_(raw) { decode(); }

void Instruction::decode() {
  opcode_ = raw_ & 0x7F;
  rd_ = (raw_ >> 7) & 0x1F;
  funct3_ = (raw_ >> 12) & 0x7;
  rs1_ = (raw_ >> 15) & 0x1F;
  rs2_ = (raw_ >> 20) & 0x1F;
  funct7_ = (raw_ >> 25) & 0x7F;

  switch (get_type()) {
  case I_TYPE:
    imm_ = decode_i_imm();
    break;
  case S_TYPE:
    imm_ = decode_s_imm();
    break;
  case B_TYPE:
    imm_ = decode_b_imm();
    break;
  case U_TYPE:
    imm_ = decode_u_imm();
    break;
  case J_TYPE:
    imm_ = decode_j_imm();
    break;
  default:
    imm_ = 0;
    break;
  }
}

Instruction::Type Instruction::get_type() const {
  switch (opcode_) {
  case 0b0110011:
    return R_TYPE;
  case 0b0010011:
    return I_TYPE;
  case 0b0000011:
    return I_TYPE;
  case 0b1100111:
    return I_TYPE;
  case 0b0100011:
    return S_TYPE;
  case 0b1100011:
    return B_TYPE;
  case 0b0110111:
    return U_TYPE;
  case 0b0010111:
    return U_TYPE;
  case 0b1101111:
    return J_TYPE;
  default:
    return UNKNOWN;
  }
}

std::string Instruction::get_mnemonic() const {
  switch (opcode_) {
  case 0b0110011:
    switch (funct3_) {
    case 0b000:
      return (funct7_ == 0) ? "add" : "sub";
    case 0b001:
      return "sll";
    case 0b010:
      return "slt";
    case 0b011:
      return "sltu";
    case 0b100:
      return "xor";
    case 0b101:
      return (funct7_ == 0) ? "srl" : "sra";
    case 0b110:
      return "or";
    case 0b111:
      return "and";
    }
    break;

  case 0b0010011:
    switch (funct3_) {
    case 0b000:
      return "addi";
    case 0b001:
      return "slli";
    case 0b010:
      return "slti";
    case 0b011:
      return "sltiu";
    case 0b100:
      return "xori";
    case 0b101:
      return (funct7_ == 0) ? "srli" : "srai";
    case 0b110:
      return "ori";
    case 0b111:
      return "andi";
    }
    break;

  case 0b0000011:
    switch (funct3_) {
    case 0b000:
      return "lb";
    case 0b001:
      return "lh";
    case 0b010:
      return "lw";
    case 0b100:
      return "lbu";
    case 0b101:
      return "lhu";
    }
    break;

  case 0b0100011:
    switch (funct3_) {
    case 0b000:
      return "sb";
    case 0b001:
      return "sh";
    case 0b010:
      return "sw";
    }
    break;

  case 0b1100011:
    switch (funct3_) {
    case 0b000:
      return "beq";
    case 0b001:
      return "bne";
    case 0b100:
      return "blt";
    case 0b101:
      return "bge";
    case 0b110:
      return "bltu";
    case 0b111:
      return "bgeu";
    }
    break;

  case 0b1101111:
    return "jal";
  case 0b1100111:
    return "jalr";
  case 0b0110111:
    return "lui";
  case 0b0010111:
    return "auipc";
  }

  return "unknown";
}

std::string Instruction::to_string() const {
  std::ostringstream oss;
  std::string mnemonic = get_mnemonic();

  oss << std::left << std::setw(8) << mnemonic;

  Type type = get_type();
  switch (type) {
  case R_TYPE:
    oss << "x" << (int)rd_ << ", x" << (int)rs1_ << ", x" << (int)rs2_;
    break;

  case I_TYPE:
    if (opcode_ == 0b0000011) {
      oss << "x" << (int)rd_ << ", " << imm_ << "(x" << (int)rs1_ << ")";
    } else {
      oss << "x" << (int)rd_ << ", x" << (int)rs1_ << ", " << imm_;
    }
    break;

  case S_TYPE:
    oss << "x" << (int)rs2_ << ", " << imm_ << "(x" << (int)rs1_ << ")";
    break;

  case B_TYPE:
    oss << "x" << (int)rs1_ << ", x" << (int)rs2_ << ", " << imm_;
    break;

  case U_TYPE:
    oss << "x" << (int)rd_ << ", 0x" << std::hex << (imm_ >> 12);
    break;

  case J_TYPE:
    oss << "x" << (int)rd_ << ", " << std::dec << imm_;
    break;

  default:
    oss << "???";
    break;
  }

  return oss.str();
}

int32_t Instruction::decode_i_imm() const {
  int32_t imm = (raw_ >> 20) & 0xFFF;
  if (imm & 0x800)
    imm |= 0xFFFFF000;
  return imm;
}

int32_t Instruction::decode_s_imm() const {
  int32_t imm = ((raw_ >> 7) & 0x1F) | ((raw_ >> 20) & 0xFE0);
  if (imm & 0x800)
    imm |= 0xFFFFF000;
  return imm;
}

int32_t Instruction::decode_b_imm() const {
  int32_t imm = ((raw_ >> 7) & 0x1E) | ((raw_ >> 20) & 0x7E0) |
                ((raw_ << 4) & 0x800) | ((raw_ >> 19) & 0x1000);
  if (imm & 0x1000)
    imm |= 0xFFFFE000;
  return imm;
}

int32_t Instruction::decode_u_imm() const { return raw_ & 0xFFFFF000; }

int32_t Instruction::decode_j_imm() const {
  int32_t imm = ((raw_ >> 20) & 0x7FE) | ((raw_ >> 9) & 0x800) |
                (raw_ & 0xFF000) | ((raw_ >> 11) & 0x100000);
  if (imm & 0x100000)
    imm |= 0xFFE00000;
  return imm;
}

uint32_t Instruction::encode_add(uint8_t rd, uint8_t rs1, uint8_t rs2) {
  return (0b0000000 << 25) | (rs2 << 20) | (rs1 << 15) | (0b000 << 12) |
         (rd << 7) | 0b0110011;
}

uint32_t Instruction::encode_addi(uint8_t rd, uint8_t rs1, int16_t imm) {
  return ((imm & 0xFFF) << 20) | (rs1 << 15) | (0b000 << 12) | (rd << 7) |
         0b0010011;
}

uint32_t Instruction::encode_lw(uint8_t rd, uint8_t rs1, int16_t offset) {
  return ((offset & 0xFFF) << 20) | (rs1 << 15) | (0b010 << 12) | (rd << 7) |
         0b0000011;
}

uint32_t Instruction::encode_sw(uint8_t rs2, uint8_t rs1, int16_t offset) {
  uint32_t imm11_5 = (offset >> 5) & 0x7F;
  uint32_t imm4_0 = offset & 0x1F;
  return (imm11_5 << 25) | (rs2 << 20) | (rs1 << 15) | (0b010 << 12) |
         (imm4_0 << 7) | 0b0100011;
}

uint32_t Instruction::encode_beq(uint8_t rs1, uint8_t rs2, int16_t offset) {
  uint32_t imm12 = (offset >> 12) & 0x1;
  uint32_t imm10_5 = (offset >> 5) & 0x3F;
  uint32_t imm4_1 = (offset >> 1) & 0xF;
  uint32_t imm11 = (offset >> 11) & 0x1;
  return (imm12 << 31) | (imm10_5 << 25) | (rs2 << 20) | (rs1 << 15) |
         (0b000 << 12) | (imm4_1 << 8) | (imm11 << 7) | 0b1100011;
}

uint32_t Instruction::encode_jal(uint8_t rd, int32_t offset) {
  uint32_t imm20 = (offset >> 20) & 0x1;
  uint32_t imm10_1 = (offset >> 1) & 0x3FF;
  uint32_t imm11 = (offset >> 11) & 0x1;
  uint32_t imm19_12 = (offset >> 12) & 0xFF;
  return (imm20 << 31) | (imm10_1 << 21) | (imm11 << 20) | (imm19_12 << 12) |
         (rd << 7) | 0b1101111;
}
