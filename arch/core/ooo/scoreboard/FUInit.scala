package arch.core.ooo

object ALUDescriptor extends FUDescriptor {
  override def name: String       = "ALU"
  override def latency: FULatency = SingleCycle
}

object MULDescriptor extends FUDescriptor {
  override def name: String       = "MUL"
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
  val ALU = FURegistry.register(ALUDescriptor)
  val MUL = FURegistry.register(MULDescriptor)
  val LSU = FURegistry.register(LSUDescriptor)
  val CSR = FURegistry.register(CSRDescriptor)
}
