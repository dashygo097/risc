package core.ex

import core.common._
import chisel3._
import chisel3.util._

class RV32ALU extends Module {
  override def desiredName: String = s"rv32_alu"

  // Inner Interfaces
  val ctrl_ext = IO(new RV32ALUCtrlExtIO).suggestName("ALU_CTRL")
  val ctrl     = Wire(new RV32ALUCtrlIO)

  val packet_ext = IO(new RV32ALUPacketExtIO).suggestName("ALU_PACKET")
  val packet     = Wire(new RV32ALUPacketIO)

  packet.result := MuxLookup(ctrl.op, 0.U)(
    Seq(
      ALUOp.ADD  -> Mux(ctrl.is_sub, ctrl.src1 - ctrl.src2, ctrl.src1 + ctrl.src2),
      ALUOp.SLL  -> (ctrl.src1 << ctrl.src2(4, 0)),
      ALUOp.SLT  -> Mux(ctrl.src1.asSInt < ctrl.src2.asSInt, 1.U, 0.U),
      ALUOp.SLTU -> Mux(ctrl.src1 < ctrl.src2, 1.U, 0.U),
      ALUOp.XOR  -> (ctrl.src1 ^ ctrl.src2),
      ALUOp.SRL  -> Mux(
        ctrl.is_sra,
        (ctrl.src1.asSInt >> ctrl.src2(4, 0)).asUInt,
        ctrl.src1 >> ctrl.src2(4, 0)
      ),
      ALUOp.OR   -> (ctrl.src1 | ctrl.src2),
      ALUOp.AND  -> (ctrl.src1 & ctrl.src2),
    )
  )

  ctrl_ext.connect(ctrl)
  packet_ext.connect(packet)
}
