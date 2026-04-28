package arch.core.lsu.riscv

import arch.core.lsu._
import arch.configs._

object RV32IMLoadUtils extends RegisteredUtils[LoadUtils] {
  override def utils: LoadUtils                 = new LoadUtils {
    override def name: String = "rv32im"

    override def decodeLoad(uop: chisel3.UInt): LoadCtrl =
      RV32ILoadUtils.utils.decodeLoad(uop)
  }
  override def factory: UtilsFactory[LoadUtils] = LoadUtilsFactory
}

object RV32IMStoreUtils extends RegisteredUtils[StoreUtils] {
  override def utils: StoreUtils                 = new StoreUtils {
    override def name: String = "rv32im"

    override def decodeStore(uop: chisel3.UInt): StoreCtrl =
      RV32IStoreUtils.utils.decodeStore(uop)
  }
  override def factory: UtilsFactory[StoreUtils] = StoreUtilsFactory
}
