package arch.core.ooo

object ALUDescriptor extends FUDescriptor {
  override def name: String       = "ALU"
  override def latency: FULatency = SingleCycle
}

object MULTDescriptor extends FUDescriptor {
  override def name: String       = "MULT"
  override def latency: FULatency = VariableLatency
}

object LSUDescriptor extends FUDescriptor {
  override def name: String       = "LSU"
  override def latency: FULatency = VariableLatency
}

object CSRDescriptor extends FUDescriptor {
  override def name: String       = "CSR"
  override def latency: FULatency = SingleCycle
}

object FUInit {
  val ALU  = FURegistry.register(ALUDescriptor)
  val MULT = FURegistry.register(MULTDescriptor)
  val LSU  = FURegistry.register(LSUDescriptor)
  val CSR  = FURegistry.register(CSRDescriptor)
}
