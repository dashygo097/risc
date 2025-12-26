package arch.core.lsu

import arch.configs._
import chisel3._

class LSU(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA)}_lsu"

  val utils = LSUUtilitiesFactory.get(p(ISA)) match {
    case Some(u) => u
    case None    => throw new Exception(s"LSU utilities for ISA ${p(ISA)} not found!")
  }

}
