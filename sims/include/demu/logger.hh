#pragma once

#include <memory>
#include <spdlog/pattern_formatter.h>
#include <spdlog/sinks/stdout_color_sinks.h>
#include <spdlog/spdlog.h>

namespace demu {

class Formatter : public spdlog::custom_flag_formatter {
public:
  void format(const spdlog::details::log_msg &msg, const std::tm &,
              spdlog::memory_buf_t &dest) override {
    std::string_view level_name;
    switch (msg.level) {
    case spdlog::level::trace:
      level_name = " TRACE";
      break;
    case spdlog::level::debug:
      level_name = " DEBUG";
      break;
    case spdlog::level::info:
      level_name = " INFO ";
      break;
    case spdlog::level::warn:
      level_name = " WARN ";
      break;
    case spdlog::level::err:
      level_name = " ERROR";
      break;
    case spdlog::level::critical:
      level_name = " CRIT ";
      break;
    default:
      level_name = " UNKN ";
      break;
    }
    dest.append(level_name.data(), level_name.data() + level_name.size());
  }

  std::unique_ptr<custom_flag_formatter> clone() const override {
    return std::make_unique<Formatter>();
  }
};

class Logger {
public:
  static void init();
  static void init(spdlog::level::level_enum level);

  static std::shared_ptr<spdlog::logger> &getDemuLogger() {
    return demu_logger_;
  }

private:
  static std::shared_ptr<spdlog::logger> demu_logger_;
};
} // namespace demu

// Logging Macros
#define DEMU_TRACE(...) ::demu::Logger::getDemuLogger()->trace(__VA_ARGS__);
#define DEMU_DEBUG(...) ::demu::Logger::getDemuLogger()->debug(__VA_ARGS__);
#define DEMU_INFO(...) ::demu::Logger::getDemuLogger()->info(__VA_ARGS__);
#define DEMU_WARN(...) ::demu::Logger::getDemuLogger()->warn(__VA_ARGS__);
#define DEMU_CRIT(...) ::demu::Logger::getDemuLogger()->critical(__VA_ARGS__);
#define DEMU_ERROR(...)                                                        \
  do {                                                                         \
    ::demu::Logger::getDemuLogger()->error(__VA_ARGS__);                       \
    std::abort();                                                              \
  } while (0);
