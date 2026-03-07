package arch.isa

import arch.configs.proto._
import chisel3.util.BitPat

object ISAProto {

  private def toEncoding(name: String, bp: BitPat): ProtoInstrEncoding =
    ProtoInstrEncoding(
      name = name,
      value = bp.value.intValue,
      mask = bp.mask.intValue,
    )

  def fromRV32I: ProtoInstrSet = {
    import RV32IInstructionSet._
    ProtoInstrSet(
      name = "rv32i",
      encodings = Seq(
        toEncoding("NOP", NOP),
        toEncoding("ADD", ADD),
        toEncoding("SUB", SUB),
        toEncoding("SLL", SLL),
        toEncoding("SLT", SLT),
        toEncoding("SLTU", SLTU),
        toEncoding("XOR", XOR),
        toEncoding("SRL", SRL),
        toEncoding("SRA", SRA),
        toEncoding("OR", OR),
        toEncoding("AND", AND),
        toEncoding("ADDI", ADDI),
        toEncoding("SLLI", SLLI),
        toEncoding("SLTI", SLTI),
        toEncoding("SLTIU", SLTIU),
        toEncoding("XORI", XORI),
        toEncoding("SRLI", SRLI),
        toEncoding("SRAI", SRAI),
        toEncoding("ORI", ORI),
        toEncoding("ANDI", ANDI),
        toEncoding("LB", LB),
        toEncoding("LH", LH),
        toEncoding("LW", LW),
        toEncoding("LBU", LBU),
        toEncoding("LHU", LHU),
        toEncoding("JALR", JALR),
        toEncoding("CSRRW", CSRRW),
        toEncoding("CSRRS", CSRRS),
        toEncoding("CSRRC", CSRRC),
        toEncoding("CSRRWI", CSRRWI),
        toEncoding("CSRRSI", CSRRSI),
        toEncoding("CSRRCI", CSRRCI),
        toEncoding("SB", SB),
        toEncoding("SH", SH),
        toEncoding("SW", SW),
        toEncoding("BEQ", BEQ),
        toEncoding("BNE", BNE),
        toEncoding("BLT", BLT),
        toEncoding("BGE", BGE),
        toEncoding("BLTU", BLTU),
        toEncoding("BGEU", BGEU),
        toEncoding("LUI", LUI),
        toEncoding("AUIPC", AUIPC),
        toEncoding("JAL", JAL),
      ),
    )
  }

  def from(isa: ISADefinition): ProtoInstrSet = isa match {
    case RV32I => fromRV32I
    case other => throw new Exception(s"No proto mapping for ISA: ${other.name}")
  }
}
