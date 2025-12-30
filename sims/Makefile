PROJECT_DIR = $(shell pwd)
CMAKE_DIR = $(PROJECT_DIR)/cmake
BUILD_DIR = $(PROJECT_DIR)/build
TESTS_DIR = $(PROJECT_DIR)/tests

# Build configuration
BUILD_TYPE ?= Release 
GENERATOR ?= Ninja 

# Detect generator command
ifeq ($(GENERATOR),Ninja)
    BUILD_CMD = ninja -C $(BUILD_DIR)
else
    BUILD_CMD = cmake --build $(BUILD_DIR)
endif

.PHONY: all config build clean help

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
build: 
	@mkdir -p $(BUILD_DIR)
	@if [ ! -f $(BUILD_DIR)/CMakeCache.txt ]; then \
		echo "Running CMake with generator: $(GENERATOR)"; \
		cd $(BUILD_DIR) && cmake $(PROJECT_DIR) \
			-DCMAKE_BUILD_TYPE=$(BUILD_TYPE) \
			-G $(GENERATOR); \
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

# Help message
help:
	@echo "Available targets:"
	@echo "  all              - Configure and build (default)"
	@echo "  config           - Configure the project with CMake"
	@echo "  build            - Build the project"
	@echo "  reconfigure      - Force reconfiguration of the project"
	@echo "  clean            - Remove build directory"
	@echo "  help             - Show this help message"
	@echo ""
	@echo "Variables:"
	@echo "  BUILD_TYPE       - Build type (Debug, Release, RelWithDebInfo, MinSizeRel)"
	@echo "                     Current: $(BUILD_TYPE)"
	@echo "  GENERATOR        - CMake generator (Ninja, 'Unix Makefiles', etc.)"
	@echo "                     Current: $(GENERATOR)"
	@echo ""
