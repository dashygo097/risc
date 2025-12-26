package arch.core.alu

import arch.configs._
import chisel3._

class ALU(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_alu"

  val utils = ALUUtilitiesFactory.get(p(ISA)) match {
    case Some(u) => u
    case None    => throw new Exception(s"ALU utilities for ISA ${p(ISA)} not found!")
  }

}
