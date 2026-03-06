#pragma once

#include "./logger.hh"
#include <filesystem>
#include <fstream>
#include <nlohmann/json.hpp>
#include <string>

namespace demu {
using namespace nlohmann;

class Config {
public:
  Config() { loadFromFile(); }

  explicit Config(const std::string &config_path) : config_file_(config_path) {
    loadFromFile();
  }

  bool loadFromFile() {
    std::string config_path = getConfigPath();

    if (!std::filesystem::exists(config_path)) {
      DEMU_ERROR("Config file not found: {}", config_path);
      return false;
    }

    try {
      std::ifstream file(config_path);
      if (!file.is_open()) {
        DEMU_ERROR("Failed to open config file: {}", config_path);
        return false;
      }

      file >> config_;
      DEMU_INFO("Successfully loaded config from: {}", config_path);

      return true;
    } catch (const std::exception &e) {
      DEMU_ERROR("Error parsing JSON config: {}", e.what());
      return false;
    }
  }

  void dump() const {
    DEMU_INFO("Configuration: ");
    DEMU_INFO("  Config file: {}", getConfigPath());
    DEMU_INFO("  RTL_DIR: {}", getRTLDir());
    DEMU_TRACE("  JSON Content:");

    std::string json_str = config_.dump(2);
    std::istringstream iss(json_str);
    std::string line;
    while (std::getline(iss, line)) {
      DEMU_TRACE("    {}", line);
    }
  }

  std::string getRTLDir() const {
#ifdef RTL_DIR
    return std::string(RTL_DIR);
#else
    DEMU_WARN("RTL_DIR not defined in compile definitions");
    return "";
#endif
  }

  std::string getConfigPath() const {
    if (!config_file_.empty()) {
      return config_file_;
    }
#ifdef RTL_CONFIG_FILE
    return std::string(RTL_CONFIG_FILE);
#else
    DEMU_WARN("RTL_CONFIG_FILE not defined in compile definitions");
    return "";
#endif
  }

  const json &getJson() const { return config_; }

  bool isValid() const { return !config_.empty(); }

  template <typename T>
  T getValue(const std::string &key, const T &default_value = T()) const {
    if (config_.contains(key)) {
      try {
        return config_[key].get<T>();
      } catch (const std::exception &e) {
        DEMU_WARN("Failed to get key '{}' as requested type: {}", key,
                  e.what());
        return default_value;
      }
    }
    return default_value;
  }

  template <typename T>
  T getNestedValue(const std::string &key_path,
                   const T &default_value = T()) const {
    try {
      const json *current = &config_;
      std::istringstream iss(key_path);
      std::string token;

      while (std::getline(iss, token, '.')) {
        if (current->contains(token)) {
          current = &(*current)[token];
        } else {
          return default_value;
        }
      }

      return current->get<T>();
    } catch (const std::exception &e) {
      DEMU_TRACE("Failed to get nested value '{}': {}", key_path, e.what());
      return default_value;
    }
  }

  bool hasKey(const std::string &key) const { return config_.contains(key); }

  bool validateRequired(const std::vector<std::string> &required_keys) const {
    bool all_valid = true;
    for (const auto &key : required_keys) {
      if (!hasKey(key)) {
        DEMU_ERROR("Required configuration key missing: {}", key);
        all_valid = false;
      }
    }
    return all_valid;
  }

  bool reload() {
    DEMU_INFO("Reloading configuration from: {}", getConfigPath());
    return loadFromFile();
  }

private:
  json config_;
  std::string config_file_;
};

} // namespace demu
