#include "demu/logger.hh"
#include <spdlog/pattern_formatter.h>
#include <spdlog/sinks/basic_file_sink.h>
#include <spdlog/sinks/stdout_color_sinks.h>
#include <vector>

namespace demu {

std::shared_ptr<spdlog::logger> Logger::demu_logger_;
std::shared_ptr<spdlog::logger> Logger::hal_logger_;

// Overload for backward compatibility
void Logger::init() { init(spdlog::level::info); }

void Logger::init(spdlog::level::level_enum level) {
  // Create sinks
  auto console_sink = std::make_shared<spdlog::sinks::stdout_color_sink_mt>();
  auto file_sink =
      std::make_shared<spdlog::sinks::basic_file_sink_mt>("demu.log", true);

  // Create formatter for console sink
  auto console_formatter = std::make_unique<spdlog::pattern_formatter>();
  console_formatter->add_flag<Formatter>('*');
  console_formatter->set_pattern("%^%*%$ \033[2mlibdemu::%n\033[0m %v");
  console_sink->set_formatter(std::move(console_formatter));

  // Set pattern for file sink
  file_sink->set_pattern("[%Y-%m-%d %T.%e] [%l] %n: %v");

  // Create logger with multiple sinks
  std::vector<spdlog::sink_ptr> sinks{console_sink, file_sink};

  // Initialize individual loggers
  demu_logger_ =
      std::make_shared<spdlog::logger>("   ", sinks.begin(), sinks.end());
  hal_logger_ =
      std::make_shared<spdlog::logger>("hal", sinks.begin(), sinks.end());

  // Register loggers with spdlog registry
  spdlog::register_logger(demu_logger_);
  spdlog::register_logger(hal_logger_);

  // Set log levels
  demu_logger_->set_level(level);
  hal_logger_->set_level(level);

  // Set flush level
  demu_logger_->flush_on(spdlog::level::err);
  hal_logger_->flush_on(spdlog::level::err);

  demu_logger_->log(spdlog::level::info, "Logger initialized with level: {}",
                    spdlog::level::to_string_view(level));
}

} // namespace demu
