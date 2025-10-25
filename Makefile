BASE_DIR = $(shell pwd)
BUILD_DIR = $(BASE_DIR)/build
SCRIPTS_DIR = $(BASE_DIR)/scripts
SIM_DIR = $(BASE_DIR)/sims
TB_DIR = $(SIM_DIR)/tb
COCOTB_DIR = $(SIM_DIR)/cocotb

LIB ?= core
FZF ?= false

.PHONY: pre fmt build run clean update localpublish tb cocotb stat-xc7

pre:
	@mkdir -p $(BUILD_DIR)
	@mkdir -p $(SIM_DIR)
	@mkdir -p $(TB_DIR)
	@mkdir -p $(COCOTB_DIR)

fmt:
	@scalafmt

build: pre 
	@sbt compile

run: pre
	@sbt $(LIB)/run

clean:
	@rm -rf $(SIM_DIR)/logs
	@rm -rf $(TB_DIR)/obj_dir
	@rm -rf $(COCOTB_DIR)/logs

update:
	@sbt clean bloopInstall
	@sbt update
	@sbt reload

localpublish:
	@sbt clean
	@sbt publishLocal

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

stat-xc7: pre
	@if [ "$(FZF)" = "true" ] ; then \
		bash $(SCRIPTS_DIR)/stat_yosys_xc7_fzf.sh ; \
	else \
		bash $(SCRIPTS_DIR)/stat_yosys_xc7.sh ; \
	fi
