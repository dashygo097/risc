#include "demu/isa/isa.hh"
#include <iomanip>
#include <sstream>

namespace demu::isa {
Instruction::Instruction(instr_t raw) : _raw(raw) { decode(); }

void Instruction::decode() {
  _opcode = _raw & 0x7F;
  _rd = (_raw >> 7) & 0x1F;
  _funct3 = (_raw >> 12) & 0x7;
  _rs1 = (_raw >> 15) & 0x1F;
  _rs2 = (_raw >> 20) & 0x1F;
  _funct7 = (_raw >> 25) & 0x7F;

  switch (type()) {
  case I_TYPE:
    _imm = decode_i_imm();
    break;
  case S_TYPE:
    _imm = decode_s_imm();
    break;
  case B_TYPE:
    _imm = decode_b_imm();
    break;
  case U_TYPE:
    _imm = decode_u_imm();
    break;
  case J_TYPE:
    _imm = decode_j_imm();
    break;
  default:
    _imm = 0;
    break;
  }
}

InstType Instruction::type() const noexcept {
  switch (_opcode) {
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
  switch (_opcode) {
  case 0b0110011:
    switch (_funct3) {
    case 0b000:
      return (_funct7 == 0) ? "add" : "sub";
    case 0b001:
      return "sll";
    case 0b010:
      return "slt";
    case 0b011:
      return "sltu";
    case 0b100:
      return "xor";
    case 0b101:
      return (_funct7 == 0) ? "srl" : "sra";
    case 0b110:
      return "or";
    case 0b111:
      return "and";
    }
    break;

  case 0b0010011:
    switch (_funct3) {
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
      return (_funct7 == 0) ? "srli" : "srai";
    case 0b110:
      return "ori";
    case 0b111:
      return "andi";
    }
    break;

  case 0b0000011:
    switch (_funct3) {
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
    switch (_funct3) {
    case 0b000:
      return "sb";
    case 0b001:
      return "sh";
    case 0b010:
      return "sw";
    }
    break;

  case 0b1100011:
    switch (_funct3) {
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
    if (_raw == 0b00000000000000000000000001110011)
      return "ecall";
    else if (_raw == EBREAK)
      return "ebreak";
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
    oss << "x" << (int)_rd << ", x" << (int)_rs1 << ", x" << (int)_rs2;
    break;

  case I_TYPE:
    if (_opcode == 0b0000011) {
      oss << "x" << (int)_rd << ", " << _imm << "(x" << (int)_rs1 << ")";
    } else {
      oss << "x" << (int)_rd << ", x" << (int)_rs1 << ", " << _imm;
    }
    break;

  case S_TYPE:
    oss << "x" << (int)_rs2 << ", " << _imm << "(x" << (int)_rs1 << ")";
    break;

  case B_TYPE:
    oss << "x" << (int)_rs1 << ", x" << (int)_rs2 << ", " << _imm;
    break;

  case U_TYPE:
    oss << "x" << (int)_rd << ", 0x" << std::hex << (_imm >> 12);
    break;

  case J_TYPE:
    oss << "x" << (int)_rd << ", " << std::dec << _imm;
    break;

  case SYSTEM:
    oss << "";
    break;

  default:
    oss << "???";
    break;
  }

  return oss.str();
}

int32_t Instruction::decode_i_imm() const noexcept {
  int32_t imm = (_raw >> 20) & 0xFFF;
  if (imm & 0x800)
    imm |= 0xFFFFF000;
  return imm;
}

int32_t Instruction::decode_s_imm() const noexcept {
  int32_t imm = ((_raw >> 7) & 0x1F) | ((_raw >> 20) & 0xFE0);
  if (imm & 0x800)
    imm |= 0xFFFFF000;
  return imm;
}

int32_t Instruction::decode_b_imm() const noexcept {
  int32_t imm = ((_raw >> 7) & 0x1E) | ((_raw >> 20) & 0x7E0) |
                ((_raw << 4) & 0x800) | ((_raw >> 19) & 0x1000);
  if (imm & 0x1000)
    imm |= 0xFFFFE000;
  return imm;
}

int32_t Instruction::decode_u_imm() const noexcept { return _raw & 0xFFFFF000; }

int32_t Instruction::decode_j_imm() const noexcept {
  int32_t imm = ((_raw >> 20) & 0x7FE) | ((_raw >> 9) & 0x800) |
                (_raw & 0xFF000) | ((_raw >> 11) & 0x100000);
  if (imm & 0x100000)
    imm |= 0xFFE00000;
  return imm;
}

} // namespace demu::isa
