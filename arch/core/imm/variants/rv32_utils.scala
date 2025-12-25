package arch.core.imm

class RV32ImmUtilitiesImpl extends ImmUtilities with RV32ImmConsts {}

object RV32ImmUtilities extends RegisteredImmUtilities with RV32ImmConsts {
  override def isaName: String     = "rv32i"
  override def utils: ImmUtilities = new RV32ImmUtilitiesImpl
}
