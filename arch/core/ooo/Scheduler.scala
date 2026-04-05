package arch.core.ooo

import arch.configs._
import chisel3._
import chisel3.util.{ Decoupled, Valid }

abstract class Scheduler(implicit p: Parameters) extends Module {
  val numFUs = p(FunctionalUnits).size

  val issueWidth = 1

  val dis_reqs = IO(Vec(issueWidth, Flipped(Decoupled(new MicroOp))))
  val fu_reqs  = IO(Vec(numFUs, Decoupled(new MicroOp)))

  val fu_done = IO(Flipped(Vec(numFUs, Valid(new FunctionalUnitResp))))
}

object Scheduler {
  def apply()(implicit p: Parameters): Scheduler =
    p(ScheduleType) match {
      case "scoreboard" => Module(new Scoreboard)
      case other        => throw new IllegalArgumentException(s"Unknown ScheduleType: $other")
    }
}
