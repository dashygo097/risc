PROJECT_DIR = $(shell pwd)
CMAKE_DIR = $(PROJECT_DIR)/cmake
BUILD_DIR = $(PROJECT_DIR)/build
RUNTIME_DIR = $(PROJECT_DIR)/runtime

# Build configuration
BUILD_TYPE ?= Release

# Auto-detect generator
ifndef GENERATOR
  ifneq ($(wildcard $(BUILD_DIR)/build.ninja),)
    # build.ninja exists, use Ninja
    GENERATOR = Ninja
  else ifneq ($(wildcard $(BUILD_DIR)/Makefile),)
    # Makefile exists, use Unix Makefiles
    GENERATOR = Unix Makefiles
  else
    # No build system configured yet, default to Ninja
    GENERATOR = Ninja
  endif
endif

# Validate generator
VALID_GENERATORS = Ninja Unix Makefiles
ifeq ($(filter $(GENERATOR),$(VALID_GENERATORS)),)
  $(error Invalid GENERATOR: $(GENERATOR). Supported generators: Ninja, Unix Makefiles)
endif

# Detect generator command
ifeq ($(GENERATOR),Ninja)
  BUILD_CMD = ninja -C $(BUILD_DIR)
  TEST_CMD = ninja -C $(BUILD_DIR) check-difftest
else ifeq ($(GENERATOR),Unix Makefiles)
  BUILD_CMD = cmake --build $(BUILD_DIR)
  TEST_CMD = cmake --build $(BUILD_DIR) --target check-difftest
else
  $(error Unsupported generator: $(GENERATOR))
endif

.PHONY: all config build reconfigure clean difftest help

all: build

# Configure CMake project
config:
	@echo "==> Configuring project..."
	@mkdir -p $(BUILD_DIR)
	@if [ ! -f $(BUILD_DIR)/config.cmake ]; then \
		echo "Copying default config.cmake to build directory..."; \
		cp $(CMAKE_DIR)/config.cmake $(BUILD_DIR)/config.cmake; \
	else \
		echo "Using existing config.cmake in build directory"; \
	fi

# Build the project
build: config
	@mkdir -p $(BUILD_DIR)
	@if [ ! -f $(BUILD_DIR)/CMakeCache.txt ]; then \
		echo "Running CMake with generator: $(GENERATOR)"; \
		cd $(BUILD_DIR) && cmake $(PROJECT_DIR) \
			-DCMAKE_BUILD_TYPE=$(BUILD_TYPE) \
			-G "$(GENERATOR)"; \
	fi
	@echo "==> Building project with $(GENERATOR)..."
	@$(BUILD_CMD)

# Force reconfigure
reconfigure:
	@echo "==> Forcing reconfiguration..."
	@rm -f $(BUILD_DIR)/CMakeCache.txt
	@$(MAKE) config

# Clean build artifacts
clean:
	@echo "==> Cleaning build directory..."
	@rm -rf $(BUILD_DIR)

# Run tests
difftest:
	@echo "==> Running difftests..."
	@$(TEST_CMD)

# Help message
help:
	@echo "Available targets:"
	@echo "  all              - Configure and build (default)"
	@echo "  config           - Configure the project with CMake"
	@echo "  build            - Build the project"
	@echo "  reconfigure      - Force reconfiguration of the project"
	@echo "  clean            - Remove build directory"
	@echo "  difftest         - Run difftests"
	@echo "  help             - Show this help message"
	@echo ""
	@echo "Variables:"
	@echo "  BUILD_TYPE       - Build type (Debug, Release, RelWithDebInfo, MinSizeRel)"
	@echo "                     Current: $(BUILD_TYPE)"
	@echo "  GENERATOR        - CMake generator (Ninja, Unix Makefiles)"
	@echo "                     Current: $(GENERATOR)"
	@echo ""
