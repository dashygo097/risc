package arch.core.alu

class RV32IALUUtilitiesImpl extends ALUUtilities with RV32IALUConsts {}

object RV32IALUUtilities extends RegisteredALUUtilities with RV32IALUConsts {
  override def isaName: String     = "rv32i"
  override def utils: ALUUtilities = new RV32IALUUtilitiesImpl
}
