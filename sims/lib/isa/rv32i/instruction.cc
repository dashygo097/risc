#include "demu/isa/isa.hh"
#include <iomanip>
#include <sstream>

namespace demu::isa {
Instruction::Instruction(instr_t raw) : raw_(raw) { decode(); }

void Instruction::decode() {
  opcode_ = raw_ & 0x7F;
  rd_ = (raw_ >> 7) & 0x1F;
  funct3_ = (raw_ >> 12) & 0x7;
  rs1_ = (raw_ >> 15) & 0x1F;
  rs2_ = (raw_ >> 20) & 0x1F;
  funct7_ = (raw_ >> 25) & 0x7F;

  switch (type()) {
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

InstType Instruction::type() const noexcept {
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
  case 0b1110011:
    return SYSTEM;
  default:
    return UNKNOWN;
  }
}

std::string Instruction::mnemonic() const noexcept {
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

  case 0b1110011:
    if (raw_ == 0b00000000000000000000000001110011)
      return "ecall";
    else if (raw_ == EBREAK)
      return "ebreak";

    switch (funct3_) {
    case 0b001:
      return "csrrw";
    case 0b010:
      return "csrrs";
    case 0b011:
      return "csrrc";
    case 0b101:
      return "csrrwi";
    case 0b110:
      return "csrrsi";
    case 0b111:
      return "csrrci";
    }
    break;
  }

  return "unknown";
}

std::string Instruction::to_string() const {
  std::ostringstream oss;
  std::string mnemonic_str = mnemonic();

  oss << std::left << std::setw(8) << mnemonic_str;

  InstType instr_type = type();
  switch (instr_type) {
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
    oss << "x" << (int)rd_ << ", 0x" << std::hex << std::setw(8)
        << std::setfill('0') << (uint32_t)imm_;
    break;

  case J_TYPE:
    oss << "x" << (int)rd_ << ", " << std::dec << imm_;
    break;

  case SYSTEM:
    if (raw_ == 0b00000000000000000000000001110011 || raw_ == EBREAK) {
      oss << "";
    } else if (funct3_ <= 0b011) {
      oss << "x" << (int)rd_ << ", x" << (int)rs1_ << ", 0x" << std::hex
          << std::setw(3) << std::setfill('0') << ((raw_ >> 20) & 0xFFF);
    } else {
      oss << "x" << (int)rd_ << ", 0x" << std::hex << std::setw(3)
          << std::setfill('0') << ((raw_ >> 20) & 0xFFF) << ", " << (int)rs1_;
    }
    break;

  default:
    oss << "???";
    break;
  }

  return oss.str();
}

int32_t Instruction::decode_i_imm() const noexcept {
  int32_t imm = (raw_ >> 20) & 0xFFF;
  if (imm & 0x800)
    imm |= 0xFFFFF000;
  return imm;
}

int32_t Instruction::decode_s_imm() const noexcept {
  int32_t imm = ((raw_ >> 7) & 0x1F) | ((raw_ >> 20) & 0xFE0);
  if (imm & 0x800)
    imm |= 0xFFFFF000;
  return imm;
}

int32_t Instruction::decode_b_imm() const noexcept {
  int32_t imm = ((raw_ >> 7) & 0x1E) | ((raw_ >> 20) & 0x7E0) |
                ((raw_ << 4) & 0x800) | ((raw_ >> 19) & 0x1000);
  if (imm & 0x1000)
    imm |= 0xFFFFE000;
  return imm;
}

int32_t Instruction::decode_u_imm() const noexcept { return raw_ & 0xFFFFF000; }

int32_t Instruction::decode_j_imm() const noexcept {
  int32_t imm = ((raw_ >> 20) & 0x7FE) | ((raw_ >> 9) & 0x800) |
                (raw_ & 0xFF000) | ((raw_ >> 11) & 0x100000);
  if (imm & 0x100000)
    imm |= 0xFFE00000;
  return imm;
}

} // namespace demu::isa
