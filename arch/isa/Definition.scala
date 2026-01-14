package arch.isa

trait InstructionSet {}

sealed trait ISADefinition {
  def name: String
  def instructionSet: InstructionSet
  def XLen: Int
  def ILen: Int
  def NumArchRegs: Int
}

case object RV32I extends ISADefinition {
  override def name           = "rv32i"
  override def instructionSet = RV32IInstructionSet
  override def XLen           = 32
  override def ILen           = 32
  override def NumArchRegs    = 32
}

object ISADefinition {
  def fromString(name: String): Option[ISADefinition] = name.toLowerCase match {
    case "rv32i" => Some(RV32I)
    case other   =>
      throw new Exception(s"Unsupported ISA: $other\n  Available ISAs: ${available.map(_.name).mkString(", ")}")
  }
  def available: Seq[ISADefinition]                   = Seq(RV32I)

  def instructionSet(isa: String): InstructionSet = fromString(isa) match {
    case Some(isaDefinition) => isaDefinition.instructionSet
    case None                => throw new Exception(s"Cannot get instruction set for unsupported ISA: $isa")
  }
  def xlen(isa: String): Int                      = fromString(isa) match {
    case Some(isaDefinition) => isaDefinition.XLen
    case None                => throw new Exception(s"Cannot determine XLEN for unsupported ISA: $isa")
  }
  def ilen(isa: String): Int                      = fromString(isa) match {
    case Some(isaDefinition) => isaDefinition.ILen
    case None                => throw new Exception(s"Cannot determine ILEN for unsupported ISA: $isa")
  }
  def numArchRegs(isa: String): Int               = fromString(isa) match {
    case Some(isaDefinition) => isaDefinition.NumArchRegs
    case None                => throw new Exception(s"Cannot determine number of architectural registers for unsupported ISA: $isa")
  }
}
