package arch.isa

trait InstructionSet {
  def NOP: chisel3.util.BitPat
}

sealed trait ISADefinition {
  def name: String
  def XLen: Int
  def ILen: Int
  def NumArchRegs: Int
  def IsBigEndian: Boolean

  def Bubble: chisel3.util.BitPat
}

case object RV32I extends ISADefinition {
  override def name        = "rv32i"
  override def XLen        = 32
  override def ILen        = 32
  override def NumArchRegs = 32
  override def IsBigEndian = false

  override def Bubble = RV32IInstructionSet.NOP
}

object ISADefinition {
  def fromString(name: String): Option[ISADefinition] = name.toLowerCase match {
    case "rv32i" => Some(RV32I)
    case other   =>
      throw new Exception(s"Unsupported ISA: $other\n  Available ISAs: ${available.map(_.name).mkString(", ")}")
  }
  def available: Seq[ISADefinition]                   = Seq(RV32I)

  def xlen(isa: String): Int                   = fromString(isa) match {
    case Some(isaDefinition) => isaDefinition.XLen
    case None                => throw new Exception(s"Cannot determine XLEN for unsupported ISA: $isa")
  }
  def ilen(isa: String): Int                   = fromString(isa) match {
    case Some(isaDefinition) => isaDefinition.ILen
    case None                => throw new Exception(s"Cannot determine ILEN for unsupported ISA: $isa")
  }
  def numArchRegs(isa: String): Int            = fromString(isa) match {
    case Some(isaDefinition) => isaDefinition.NumArchRegs
    case None                => throw new Exception(s"Cannot determine number of architectural registers for unsupported ISA: $isa")
  }
  def isBigEndian(isa: String): Boolean        = fromString(isa) match {
    case Some(isaDefinition) => isaDefinition.IsBigEndian
    case None                => throw new Exception(s"Cannot determine endianness for unsupported ISA: $isa")
  }
  def bubble(isa: String): chisel3.util.BitPat = fromString(isa) match {
    case Some(isaDefinition) => isaDefinition.Bubble
    case None                => throw new Exception(s"Cannot determine bubble instruction for unsupported ISA: $isa")
  }
}
