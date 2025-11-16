BASE_DIR = $(shell pwd)
BUILD_DIR = $(BASE_DIR)/build
SCRIPTS_DIR = $(BASE_DIR)/scripts
SIM_DIR = $(BASE_DIR)/sims
SYNTH_DIR = $(BASE_DIR)/synth
TB_DIR = $(SIM_DIR)/tb
COCOTB_DIR = $(SIM_DIR)/cocotb

LIB ?= app
FZF ?= false
STA_TOOL ?= yosys

.PHONY: pre fmt build run clean update localpublish tb cocotb sta sta-yosys sta-vivado

pre:
	@mkdir -p $(BUILD_DIR)
	@mkdir -p $(SIM_DIR)
	@mkdir -p $(SYNTH_DIR)
	@mkdir -p $(TB_DIR)
	@mkdir -p $(COCOTB_DIR)

fmt:
	@scalafmt

build: pre 
	@sbt compile

run: pre
	@sbt $(LIB)/run

clean:
	@rm -rf $(SYNTH_DIR)
	@rm -rf $(SIM_DIR)/logs
	@rm -rf $(SIM_DIR)/build

update:
	@sbt clean bloopInstall
	@sbt update
	@sbt reload

tb: pre
	@if [ "$(FZF)" = "true" ] ; then \
		bash $(SCRIPTS_DIR)/tb_fzf.sh ; \
	else \
		bash $(SCRIPTS_DIR)/tb.sh ; \
	fi

cocotb: pre
	@touch $(COCOTB_DIR)/cocotb.make
	@echo "TOPLEVEL_LANG ?= verilog" > $(COCOTB_DIR)/cocotb.make
	@echo "SIM = icarus" >> $(COCOTB_DIR)/cocotb.make
	@echo "" >> $(COCOTB_DIR)/cocotb.make
	@echo "include $(shell cocotb-config --makefiles)/Makefile.sim" >> $(COCOTB_DIR)/cocotb.make
	@if [ "$(FZF)" = "true" ] ; then \
		bash $(SCRIPTS_DIR)/cocotb_fzf.sh; \
	else \
		bash $(SCRIPTS_DIR)/cocotb.sh; \
	fi

sta-yosys: pre
	@if [ "$(FZF)" = "true" ] ; then \
		FZF=true bash $(SCRIPTS_DIR)/sta_yosys.sh ; \
	else \
		bash $(SCRIPTS_DIR)/sta_yosys.sh ; \
	fi

sta-vivado: pre
	@if [ "$(FZF)" = "true" ] ; then \
		FZF=true bash $(SCRIPTS_DIR)/sta_vivado.sh ; \
	else \
		bash $(SCRIPTS_DIR)/sta_vivado.sh ; \
	fi

sta: pre
	@if [ "$(STA_TOOL)" = "yosys" ] ; then \
		$(MAKE) sta-yosys FZF=$(FZF) ; \
	elif [ "$(STA_TOOL)" = "vivado" ] ; then \
		$(MAKE) sta-vivado FZF=$(FZF) ; \
	else \
		echo "Unsupported STA_TOOL: $(STA_TOOL)" ; \
		exit 1 ; \
	fi
