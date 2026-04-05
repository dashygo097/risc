package arch.core.ooo

import arch.configs._
import chisel3._
import chisel3.util.{ Decoupled, Valid }

abstract class Scheduler(implicit p: Parameters) extends Module {
  val dis_reqs = IO(Vec(p(IssueWidth), Flipped(Decoupled(new MicroOp))))
  val fu_reqs  = IO(Vec(p(FunctionalUnits).size, Decoupled(new MicroOp)))
  val fu_done  = IO(Flipped(Vec(p(FunctionalUnits).size, Valid(new FunctionalUnitResp))))

  val flush = IO(Input(Bool()))
}

object Scheduler {
  def apply()(implicit p: Parameters): Scheduler =
    p(ScheduleType) match {
      case "scoreboard" => Module(new Scoreboard)
      case other        => throw new IllegalArgumentException(s"Unknown ScheduleType: $other")
    }
}
