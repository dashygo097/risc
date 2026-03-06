package arch.system

case class DeviceDescriptor(
  name: String,
  deviceType: String,
  startAddr: Long,
  endAddr: Long
)
