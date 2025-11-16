#!/bin/bash

BASE_DIR=$(dirname $(cd "$(dirname "$0")" && pwd))
BUILD_DIR=$BASE_DIR/build
SIM_DIR=$BASE_DIR/sims
COCOTB_DIR=$BASE_DIR/sims/cocotb
COCOTB_MAKEFILE="$COCOTB_DIR/cocotb.make"

RED='\033[1;31m'
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
BLUE='\033[1;34m'
MAGENTA='\033[1;35m'
CYAN='\033[1;36m'
GRAY='\033[1;37m'
NC='\033[0m' 
BOLD='\033[1m'
DIM='\033[2m'

show_header() {
  echo -e "${BLUE}"
  echo "  ████████╗███████╗███████╗████████╗██████╗ ███████╗███╗   ██╗ ██████╗██╗  ██╗"
  echo "  ╚══██╔══╝██╔════╝██╔════╝╚══██╔══╝██╔══██╗██╔════╝████╗  ██║██╔════╝██║  ██║"
  echo "     ██║   █████╗  ███████╗   ██║   ██████╔╝█████╗  ██╔██╗ ██║██║     ███████║"
  echo "     ██║   ██╔══╝  ╚════██║   ██║   ██╔══██╗██╔══╝  ██║╚██╗██║██║     ██╔══██║"
  echo "     ██║   ███████╗███████║   ██║   ██████╔╝███████╗██║ ╚████║╚██████╗██║  ██║"
  echo "     ╚═╝   ╚══════╝╚══════╝   ╚═╝   ╚═════╝ ╚══════╝╚═╝  ╚═══╝ ╚═════╝╚═╝  ╚═╝"
  echo -e "${NC}"
  echo -e "${DIM}Testbench Automation Tool${NC}"
  echo -e "${DIM}──────────────────────────────────────────────────────────${NC}"
  echo
}

show_status() {
  local status=$1
  local message=$2
  
  case $status in
    "info") echo -e "${DIM}│  ${message}${NC}" >&2;;
    "success") echo -e "${GREEN}✔  ${message}${NC}" >&2;;
    "warning") echo -e "${YELLOW}│  ${message}${NC}" >&2;;
    "error") echo -e "${RED}✖  ${message}${NC}" >&2;;
    *) echo -e "${DIM}│  ${message}${NC}" >&2;;
  esac
}

select_dut() {
  if [[ "$FZF" == "true" ]]; then
    echo -e "${DIM}◇ Select a DUT: ${NC}" >&2
    local dut_file=$(find "$BUILD_DIR" -type f \( -name "*.v" \) | sed "s|^$BUILD_DIR/||" | fzf --height=30% --prompt="Fuzzy Search: " --header="Use arrow keys to navigate, Enter to select")
    if [ -z "$dut_file" ]; then
      echo -e "${RED}✖  No DUT selected. Skip.${NC}" >&2
      exit 1
    fi
    echo -e "\033[1A\033[2K${GREEN}◆ Selected: $(basename "$dut_file")${NC} ($dut_file)" >&2
    echo $dut_file
  else
    echo -e "${DIM}◇ Available testbenches:${NC}" >&2
    
    local tb_files=()
    while IFS= read -r -d $'\0' file; do
      tb_files+=("${file#$BUILD_DIR/}")
    done < <(find "$BUILD_DIR" -type f \( -name "*.v" \) -print0)
    
    if [ ${#tb_files[@]} -eq 0 ]; then
      echo -e "${RED}✖ No testbench files found.${NC}" >&2
      exit 1
    fi
    
    for i in "${!tb_files[@]}"; do
      echo -e "  ${GRAY}$((i+1)))${NC} ${tb_files[$i]}" >&2
    done
    
    local selected
    while true; do
      echo -ne "${YELLOW}? Select testbench (1-${#tb_files[@]}): ${NC}" >&2
      read -r selected
      
      if [[ "$selected" =~ ^[0-9]+$ ]] && \
        [ "$selected" -ge 1 ] && \
        [ "$selected" -le ${#tb_files[@]} ]; then
        break
      elif [[ "$selected" = "q" || "$selected" = "Q" ]]; then
        echo -e "${RED}✖ Exiting.${NC}" >&2
        exit 0
      else
        echo -e "${RED}Invalid selection. Please enter a number between 1 and ${#tb_files[@]}.${NC}" >&2
      fi
    done
    
    local tb_file="${tb_files[$((selected-1))]}"
    echo -e "\033[1A\033[2K${GREEN}◆ Selected: $tb_file${NC}" >&2
    
    echo "$tb_file"
  fi
}
 

fetch_top_module() {
  local flag=0 
  echo -ne "${DIM}│  Enter the top module name from the DUT file: ${NC}" >&2

  read -r top_module
  while [[ -z "$top_module" ]]; do
    echo -ne "\033[1A\033[2K" >&2
    show_status "warning" "Top module name cannot be empty!"
    read -r top_module
    flag=1
  done

  if [[ $flag -eq 1 ]]; then
    echo -ne "\033[1A\033[2K" >&2
  fi
  show_status "success" "Top module name set to: $top_module"

  echo "$top_module"
}

select_testbench() {
  if [[ "$FZF" == "true" ]]; then
    echo -e "${DIM}◇ Select a testbench: ${NC}" >&2
    local tb_file=$(find "$COCOTB_DIR" -type f \( -name "*.py" \) | sed "s|^$COCOTB_DIR/||" | fzf --height=30% --prompt="Fuzzy Search: " --header="Use arrow keys to navigate, Enter to select")
    
    if [ -z "$tb_file" ]; then
      echo -e "${RED}✖  No testbench selected. ${NC}" >&2
      exit 1
    fi
    
    echo -e "\033[1A\033[2K${GREEN}◆ Selected: $(basename "$tb_file")${NC} ($tb_file)" >&2

    echo "$(basename "$tb_file")"
  else
    echo -e "${DIM}◇ Available testbenches:${NC}" >&2
    
    local tb_files=()
    while IFS= read -r -d $'\0' file; do
      tb_files+=("${file#$TB_DIR/}")
    done < <(find "$COCOTB_DIR" -type f \( -name "*.py" \) -print0)
    
    if [ ${#tb_files[@]} -eq 0 ]; then
      echo -e "${RED}✖ No testbench files found.${NC}" >&2
      exit 1
    fi
    
    for i in "${!tb_files[@]}"; do
      echo -e "  ${GRAY}$((i+1)))${NC} $(basename "${tb_files[$i]}")" >&2
    done
    
    local selected
    while true; do
      echo -ne "${YELLOW}? Select testbench (1-${#tb_files[@]}): ${NC}" >&2
      read -r selected
      
      if [[ "$selected" =~ ^[0-9]+$ ]] && \
        [ "$selected" -ge 1 ] && \
        [ "$selected" -le ${#tb_files[@]} ]; then
        break
      elif [[ "$selected" = "q" || "$selected" = "Q" ]]; then
        echo -e "${RED}✖  Exiting.${NC}" >&2
        exit 0
      else
        echo -e "${RED}Invalid selection. Please enter a number between 1 and ${#tb_files[@]}.${NC}" >&2
      fi
    done
    
    local tb_file="${tb_files[$((selected-1))]}"
    echo -e "\033[1A\033[2K${GREEN}◆ Selected: $(basename "$tb_file")${NC} ($tb_file)" >&2
    
    echo $(basename "$tb_file")
  fi
}

run_test() {
  show_header
  dut_file="$(select_dut)"
  if [ -z "$dut_file" ]; then
    show_status "error" "DUT not selected. Exiting."
    exit 1
  fi
  top_module="$(fetch_top_module)"

  cd "$COCOTB_DIR" || exit
  tb_file="$(select_testbench)"
  if [ -z "$tb_file" ]; then
    show_status "error" "Testbench not selected. Exiting."
    exit 1
  fi

  show_status "info" "Running cocotb testbench for DUT: $dut_file"
  show_status "info" "Using testbench: $tb_file"
  show_status "info" "Top module: $top_module"
  echo -e "${DIM}◇ Running cocotb testbench...${NC}" >&2
  export PYTHONPATH="$COCOTB_DIR:$PYTHONPATH"
  make -f $COCOTB_MAKEFILE \
    VERILOG_SOURCES="$dut_file" \
    TOPLEVEL="$top_module" \
    COCOTB_TEST_MODULES="${tb_file%.*}" \
    -C "$BUILD_DIR" || {
      show_status "error" "Failed to run cocotb testbench."
      exit 1
    }
  rm -rf "$SIM_DIR/logs/${tb_file%.*}"
  rm -rf "$SIM_DIR/build/${tb_file%.*}"
  mkdir -p "$SIM_DIR/logs/${tb_file%.*}"
  mkdir -p "$SIM_DIR/build"
  mv "$BUILD_DIR/sim_build" "$SIM_DIR/build/${tb_file%.*}"
  mv "$BUILD_DIR/results.xml" "$SIM_DIR/logs/${tb_file%.*}/results.xml"

  show_status "success" "Cocotb testbench executed successfully."
}

# Main execution
run_test
