#include "demu/logger.hh"
#include <spdlog/pattern_formatter.h>
#include <spdlog/sinks/basic_file_sink.h>
#include <vector>

namespace demu {

std::shared_ptr<spdlog::logger> Logger::demu_logger_;
std::shared_ptr<spdlog::logger> Logger::cpu_logger_;
std::shared_ptr<spdlog::logger> Logger::system_logger_;

void Logger::init() {
  std::vector<spdlog::sink_ptr> sinks;
  auto formatter = std::make_unique<spdlog::pattern_formatter>();

  formatter->add_flag<Formatter>('*');
  formatter->set_pattern("%^%*%$ \033[2mlibdemu::%n\033[0m %v");

  sinks.push_back(std::make_shared<spdlog::sinks::stdout_color_sink_mt>());
  sinks.push_back(
      std::make_shared<spdlog::sinks::basic_file_sink_mt>("demu.log", true));

  for (auto &sink : sinks) {
  }

  sinks[0]->set_formatter(std::move(formatter));
  sinks[1]->set_pattern("[%T] [%l] %n: %v");

  // Initialize individual loggers
  demu_logger_ =
      std::make_shared<spdlog::logger>("demu", sinks.begin(), sinks.end());
  cpu_logger_ =
      std::make_shared<spdlog::logger>("cpu", sinks.begin(), sinks.end());
  system_logger_ =
      std::make_shared<spdlog::logger>("system", sinks.begin(), sinks.end());

  // Set levels
  demu_logger_->set_level(spdlog::level::trace);
  cpu_logger_->set_level(spdlog::level::trace);
  system_logger_->set_level(spdlog::level::trace);
}
} // namespace demu
