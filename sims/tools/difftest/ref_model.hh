#pragma once

#include <cstdint>
#include <memory>
#include <string>

namespace demu::difftest {

struct riscv32_CPU_state {
  uint32_t gpr[32];
  uint32_t pc;
};

class IRefModel {
public:
  virtual ~IRefModel() = default;

  virtual auto init() -> bool = 0;
  virtual void sync_memory(uint32_t addr, size_t size, const void *data) = 0;
  virtual void step(uint64_t n) = 0;

  virtual void push_state() = 0;
  virtual void pull_state() = 0;

  [[nodiscard]] virtual auto get_pc() const -> uint32_t = 0;
  [[nodiscard]] virtual auto get_reg(uint8_t idx) const -> uint32_t = 0;
  virtual void set_pc(uint32_t pc) = 0;
  virtual void set_reg(uint8_t idx, uint32_t val) = 0;
};

auto create_ref_model(const std::string &so_path) -> std::unique_ptr<IRefModel>;

} // namespace demu::difftest
