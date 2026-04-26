package arch.core.issue

import arch.configs._
import arch.configs.proto.FunctionalUnitType
import chisel3._
import chisel3.util.{ log2Ceil, PopCount, MuxLookup }
import scala.math.max

class IssueUnit(implicit p: Parameters) extends Module {
  val inst_type      = IO(Input(Vec(p(IssueWidth), UInt(log2Ceil(FunctionalUnitType.values.size).W))))
  val wants_to_issue = IO(Input(Vec(p(IssueWidth), Bool())))
  val intra_hazard   = IO(Input(Vec(p(IssueWidth), Bool())))

  val struct_hazard = IO(Output(Vec(p(IssueWidth), Bool())))
  val target_fu_id  = IO(Output(Vec(p(IssueWidth), UInt(log2Ceil(p(FunctionalUnits).size).W))))

  def getIds(fuType: FunctionalUnitType): Seq[UInt] =
    p(FunctionalUnits).zipWithIndex
      .filter(_._1.`type` == fuType)
      .map(_._2.U)

  val aluIds  = getIds(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU)
  val multIds = getIds(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_MULT)
  val divIds  = getIds(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV)
  val lsuIds  = getIds(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LSU)
  val bruIds  = getIds(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU)
  val csrIds  = getIds(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR)

  val alu_rr = RegInit(0.U(log2Ceil(max(aluIds.length, 1) + 1).W))
  val lsu_rr = RegInit(0.U(log2Ceil(max(lsuIds.length, 1) + 1).W))

  def getId(used: UInt, ids: Seq[UInt], rr: UInt = 0.U): UInt =
    if (ids.isEmpty) 0.U
    else
      MuxLookup((used + rr) % ids.length.U, ids.head)(
        ids.zipWithIndex.map { case (id, idx) => idx.U -> id }
      )

  def countUsed(w: Int, fuType: UInt): UInt =
    PopCount((0 until w).map { i =>
      wants_to_issue(i) &&
      !intra_hazard(i) &&
      inst_type(i) === fuType &&
      !struct_hazard(i)
    })

  for (w <- 0 until p(IssueWidth)) {
    val alu_used  = countUsed(w, FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU.index.U)
    val lsu_used  = countUsed(w, FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LSU.index.U)
    val div_used  = countUsed(w, FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV.index.U)
    val mult_used = countUsed(w, FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_MULT.index.U)
    val bru_used  = countUsed(w, FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU.index.U)
    val csr_used  = countUsed(w, FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR.index.U)

    struct_hazard(w) := MuxLookup(inst_type(w), false.B)(
      Seq(
        FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU.index.U  -> (alu_used >= aluIds.length.U),
        FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LSU.index.U  -> (lsu_used >= lsuIds.length.U),
        FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV.index.U  -> (div_used >= divIds.length.U),
        FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_MULT.index.U -> (mult_used >= multIds.length.U),
        FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU.index.U  -> (bru_used >= bruIds.length.U),
        FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR.index.U  -> (csr_used >= csrIds.length.U)
      )
    )

    target_fu_id(w) := MuxLookup(inst_type(w), getId(alu_used, aluIds, alu_rr))(
      Seq(
        FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LSU.index.U  -> getId(lsu_used, lsuIds, lsu_rr),
        FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV.index.U  -> getId(div_used, divIds),
        FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_MULT.index.U -> getId(mult_used, multIds),
        FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU.index.U  -> getId(bru_used, bruIds),
        FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR.index.U  -> getId(csr_used, csrIds)
      )
    )
  }

  val alu_disp = PopCount((0 until p(IssueWidth)).map { w =>
    wants_to_issue(w) && !intra_hazard(w) &&
    inst_type(w) === FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU.index.U &&
    !struct_hazard(w)
  })

  val lsu_disp = PopCount((0 until p(IssueWidth)).map { w =>
    wants_to_issue(w) && !intra_hazard(w) &&
    inst_type(w) === FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LSU.index.U &&
    !struct_hazard(w)
  })

  if (aluIds.nonEmpty) alu_rr := (alu_rr + alu_disp) % aluIds.length.U
  if (lsuIds.nonEmpty) lsu_rr := (lsu_rr + lsu_disp) % lsuIds.length.U
}
