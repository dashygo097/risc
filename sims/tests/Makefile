ASM_DIR    := asm
ELF_DIR 	 := elf
BIN_DIR    := bin
HEX_DIR    := hex
DUMP_DIR  := dump
LINKER_SCRIPT := linker.ld

CROSS_PREFIX := riscv64-unknown-elf-
AS      := $(CROSS_PREFIX)as
LD      := $(CROSS_PREFIX)ld
OBJCOPY := $(CROSS_PREFIX)objcopy
OBJDUMP := $(CROSS_PREFIX)objdump
CC      := $(CROSS_PREFIX)gcc

ASFLAGS := -march=rv32i -mabi=ilp32
LDFLAGS := -m elf32lriscv -T $(LINKER_SCRIPT)

RED     := \033[1;31m
GREEN   := \033[1;32m
YELLOW  := \033[1;33m
BLUE    := \033[1;34m
MAGENTA := \033[1;35m
CYAN    := \033[1;36m
RESET   := \033[0m

ASM_SOURCES := $(wildcard $(ASM_DIR)/*.asm)
S_SOURCES   := $(wildcard $(ASM_DIR)/*.S) 

ASM_TESTS := $(patsubst $(ASM_DIR)/%.asm,%,$(ASM_SOURCES))
S_TESTS   := $(patsubst $(ASM_DIR)/%.S,%,$(S_SOURCES))

ALL_TESTS := $(ASM_TESTS) $(S_TESTS)

ELF_FILES  := $(addprefix $(ELF_DIR)/,$(addsuffix .elf,$(ALL_TESTS)))
BIN_FILES  := $(addprefix $(BIN_DIR)/,$(addsuffix .bin,$(ALL_TESTS)))
HEX_FILES  := $(addprefix $(HEX_DIR)/,$(addsuffix .hex,$(ALL_TESTS)))
DUMP_FILES := $(addprefix $(DUMP_DIR)/,$(addsuffix .dump,$(ALL_TESTS)))

.PHONY: pre all clean help list check

pre:
	@mkdir -p ${BIN_DIR} $(ELF_DIR) $(HEX_DIR) $(DUMP_DIR)

all: pre check $(BIN_DIR) $(ELF_FILES) $(BIN_FILES) $(HEX_FILES) $(DUMP_FILES)
	@echo "$(GREEN)✓ All tests built successfully$(RESET)"
	@echo "$(CYAN)Generated $(words $(ALL_TESTS)) test binaries$(RESET)"

check:
	@which $(AS) > /dev/null 2>&1 || \
		(echo "$(RED)Error: RISC-V toolchain not found!$(RESET)" && \
		 exit 1)
	@echo "$(GREEN)✓ RISC-V toolchain found$(RESET)"

$(BIN_DIR):
	@mkdir -p $(BIN_DIR)
	@echo "$(CYAN)Created $(BIN_DIR) directory$(RESET)"

$(ELF_DIR)/%.elf: $(ASM_DIR)/%.asm $(LINKER_SCRIPT)
	@echo "$(BLUE)Building $@$(RESET)"
	$(AS) $(ASFLAGS) $< -o $(BIN_DIR)/$*.o
	$(LD) $(LDFLAGS) $(BIN_DIR)/$*.o -o $@
	@rm -f $(BIN_DIR)/$*.o

$(ELF_DIR)/%.elf: $(ASM_DIR)/%.S $(LINKER_SCRIPT)
	@echo "$(BLUE)Building $@$(RESET)"
	$(AS) $(ASFLAGS) $< -o $(BIN_DIR)/$*.o
	$(LD) $(LDFLAGS) $(BIN_DIR)/$*.o -o $@
	@rm -f $(BIN_DIR)/$*.o

$(BIN_DIR)/%.bin: $(ELF_DIR)/%.elf
	@echo "$(MAGENTA)Generating binary: $@$(RESET)"
	$(OBJCOPY) -O binary $< $@

$(HEX_DIR)/%.hex: $(ELF_DIR)/%.elf
	@echo "$(MAGENTA)Generating hex: $@$(RESET)"
	$(OBJCOPY) -O verilog $< $@

$(DUMP_DIR)/%.dump: $(ELF_DIR)/%.elf
	@echo "$(MAGENTA)Generating disassembly: $@$(RESET)"
	$(OBJDUMP) -d -M no-aliases $< > $@

list:
	@echo "$(CYAN)Available tests:$(RESET)"
	@for test in $(ALL_TESTS); do \
		echo "  - $$test"; \
	done
	@echo "$(CYAN)Total: $(words $(ALL_TESTS)) tests$(RESET)"

clean:
	@echo "$(YELLOW)Cleaning tests...$(RESET)"
	rm -rf $(BIN_DIR)
	rm -rf $(ELF_DIR)
	rm -rf $(HEX_DIR)
	rm -rf $(DUMP_DIR)
	@echo "$(GREEN)✓ Clean complete$(RESET)"

help:
	@echo "$(CYAN)RISC-V Test Suite Makefile$(RESET)"
	@echo ""
	@echo "$(YELLOW)Targets:$(RESET)"
	@echo "  $(GREEN)make all$(RESET)       - Build all tests"
	@echo "  $(GREEN)make clean$(RESET)              - Remove all generated files"
	@echo "  $(GREEN)make list$(RESET)               - List all available tests"
	@echo "  $(GREEN)make help$(RESET)               - Show this help message"
	@echo "  $(GREEN)make <test>.elf$(RESET)         - Build specific test ELF"
	@echo "  $(GREEN)make <test>.bin$(RESET)         - Build specific test binary"
	@echo "  $(GREEN)make <test>.hex$(RESET)         - Build specific test hex"
	@echo "  $(GREEN)make <test>.dump$(RESET)        - Build specific test disassembly"
	@echo ""
	@echo "$(YELLOW)Examples:$(RESET)"
	@echo "  make elf/test_addi.elf"
	@echo "  make hex/test_addi.hex"
	@echo "  make dump/test_addi.dump"
	@echo ""
	@echo "$(YELLOW)Directory Structure:$(RESET)"
	@echo "  $(ASM_DIR)/          - Assembly source files (.asm, .S, .c)"
	@echo "  $(ELF_DIR)/          - Generated ELF files"
	@echo "  $(BIN_DIR)/          - Generated binaries (created automatically)"
	@echo "  $(HEX_DIR)/          - Generated hex files"
	@echo "  $(DUMP_DIR)/         - Generated disassembly listings"
	@echo "  $(LINKER_SCRIPT)     - Linker script for memory layout"
	@echo ""
	@echo "$(YELLOW)Output Files:$(RESET)"
	@echo "  .elf        - Executable and Linkable Format (with symbols)"
	@echo "  .bin        - Raw binary format"
	@echo "  .hex        - Verilog memory hex format"
	@echo "  .dump       - Disassembly listing"

info:
	@echo "$(CYAN)═══════════════════════════════════════════$(RESET)"
	@echo "$(CYAN)     RISC-V Test Build Configuration$(RESET)"
	@echo "$(CYAN)═══════════════════════════════════════════$(RESET)"
	@echo "$(YELLOW)Toolchain:$(RESET)"
	@echo "  Assembler:   $(AS)"
	@echo "  Linker:      $(LD)"
	@echo "  Objcopy:     $(OBJCOPY)"
	@echo "  Objdump:     $(OBJDUMP)"
	@echo ""
	@echo "$(YELLOW)Flags:$(RESET)"
	@echo "  ASFLAGS:     $(ASFLAGS)"
	@echo "  LDFLAGS:     $(LDFLAGS)"
	@echo ""
	@echo "$(YELLOW)Sources:$(RESET)"
	@echo "  .asm files:  $(words $(ASM_SOURCES))"
	@echo "  .S files:    $(words $(S_SOURCES))"
	@echo "  Total tests: $(words $(ALL_TESTS))"
	@echo "$(CYAN)═══════════════════════════════════════════$(RESET)"

debug:
	@echo "ASM_SOURCES: $(ASM_SOURCES)"
	@echo "S_SOURCES:   $(S_SOURCES)"
	@echo "ALL_TESTS:   $(ALL_TESTS)"
	@echo "ELF_FILES:   $(ELF_FILES)"
	@echo "BIN_FILES:   $(BIN_FILES)"
	@echo "HEX_FILES:   $(HEX_FILES)"
	@echo "DUMP_FILES:  $(DUMP_FILES)"
