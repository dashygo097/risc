#pragma once

#include "../../port_handler.hh"
#include "./signals.hh"
#include "./sram.hh"
#include <type_traits>

namespace demu::hal::sram {

template <typename T, typename = void> struct sram_data_traits {
  static constexpr size_t width = std::tuple_size_v<T>;
  static auto get(const T &d, size_t i) -> word_t { return d[i]; }
  static void set(T &d, size_t i, word_t v) { d[i] = v; }
};

template <typename T>
struct sram_data_traits<T, std::enable_if_t<std::is_scalar_v<T>>> {
  static constexpr size_t width = 1;
  static auto get(const T &d, size_t) -> word_t { return static_cast<word_t>(d); }
  static void set(T &d, size_t, word_t v) { d = static_cast<T>(v); }
};

template <typename T> class CachePortHandler final : public hal::PortHandler {
  using traits = sram_data_traits<T>;
  static constexpr size_t N = traits::width;

public:
  using SignalProvider = std::function<CacheSignals<T>()>;

  explicit CachePortHandler(SignalProvider provider)
      : provider_(std::move(provider)) {}

  void handle(hal::Hardware *hw) noexcept override {
    auto *sram = dynamic_cast<SRAM *>(hw);
    if (!sram) {
      return;
}

    auto s = provider_();

    *s.req.ready = 1;
    *s.resp.valid = 1;

    if (!*s.req.valid) {
      return;
}

    if (*s.req.op == 0) {
      for (size_t i = 0; i < N; ++i) {
        const addr_t word_addr =
            *s.req.addr + static_cast<addr_t>(i * sizeof(word_t));
        traits::set(*s.resp.data, i,
                    sram->allocator()->read<word_t>(word_addr));
      }
    } else {
      for (size_t i = 0; i < N; ++i) {
        const addr_t word_addr =
            *s.req.addr + static_cast<addr_t>(i * sizeof(word_t));
        sram->allocator()->write<word_t>(word_addr,
                                         traits::get(*s.req.data, i));
      }
    }
  }

  [[nodiscard]] auto protocol_name() const noexcept -> const char * override { return "Cache"; }

private:
  SignalProvider provider_;
};

template <typename T>
class CacheReadOnlyPortHandler final : public hal::PortHandler {
  using traits = sram_data_traits<T>;
  static constexpr size_t N = traits::width;

public:
  using SignalProvider = std::function<CacheReadOnlySignals<T>()>;

  explicit CacheReadOnlyPortHandler(SignalProvider provider)
      : provider_(std::move(provider)) {}

  void handle(hal::Hardware *hw) noexcept override {
    auto *sram = dynamic_cast<SRAM *>(hw);
    if (!sram) {
      return;
}

    auto s = provider_();

    *s.req.ready = 1;
    *s.resp.valid = 1;

    if (!*s.req.valid) {
      return;
}

    for (size_t i = 0; i < N; ++i) {
      const addr_t word_addr =
          *s.req.addr + static_cast<addr_t>(i * sizeof(word_t));
      traits::set(*s.resp.data, i, sram->allocator()->read<word_t>(word_addr));
    }
  }

  [[nodiscard]] auto protocol_name() const noexcept -> const char * override {
    return "Cache Read-Only";
  }

private:
  SignalProvider provider_;
};

} // namespace demu::hal::sram
