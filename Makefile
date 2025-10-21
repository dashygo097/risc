BASE_DIR = $(shell pwd)
BUILD_DIR = $(BASE_DIR)/build
SCRIPTS_DIR = $(BASE_DIR)/scripts
SIM_DIR = $(BASE_DIR)/sims
TB_DIR = $(SIM_DIR)/tb
COCOTB_DIR = $(SIM_DIR)/cocotb

.PHONY: pre fmt build run clean update tb tb-fzf cocotb cocotb-fzf stat-xc7 stat-xc7-fzf

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
	@sbt core/run

clean:
	@rm -rf $(SIM_DIR)/logs
	@rm -rf $(TB_DIR)/obj_dir
	@rm -rf $(COCOTB_DIR)/logs

update:
	@sbt clean bloopInstall
	@sbt update
	@sbt reload

tb: pre
	@bash $(SCRIPTS_DIR)/tb.sh

tb-fzf: pre
	@bash $(SCRIPTS_DIR)/tb_fzf.sh

cocotb: pre
	@touch $(COCOTB_DIR)/cocotb.make
	@echo "TOPLEVEL_LANG ?= verilog" > $(COCOTB_DIR)/cocotb.make
	@echo "SIM = icarus" >> $(COCOTB_DIR)/cocotb.make
	@echo "" >> $(COCOTB_DIR)/cocotb.make
	@echo "include $(shell cocotb-config --makefiles)/Makefile.sim" >> $(COCOTB_DIR)/cocotb.make

	@bash $(SCRIPTS_DIR)/cocotb.sh

cocotb-fzf: pre
	@touch $(COCOTB_DIR)/cocotb.make
	@echo "TOPLEVEL = $(TB_DIR)/tb.v" > $(COCOTB_DIR)/cocotb.make
	@echo "MODULE = tb" >> $(COCOTB_DIR)/cocotb.make
	@echo "" >> $(COCOTB_DIR)/cocotb.make
	@echo "include $(shell cocotb-config --makefiles)/Makefile.sim" >> $(COCOTB_DIR)/cocotb.make

	@bash $(SCRIPTS_DIR)/cocotb_fzf.sh

stat-xc7: pre
	@bash $(SCRIPTS_DIR)/stat_yosys_xc7.sh	

stat-xc7-fzf: pre
	@bash $(SCRIPTS_DIR)/stat_yosys_xc7_fzf.sh

