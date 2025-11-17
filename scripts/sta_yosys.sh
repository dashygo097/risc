#!/bin/bash

BASE_DIR=$(dirname $(cd "$(dirname "$0")" && pwd))
BUILD_DIR=$BASE_DIR/build
SYNTH_DIR=$BASE_DIR/synth

# Colors
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
    echo " ██╗   ██╗ ██████╗ ███████╗██╗   ██╗███████╗"
    echo " ╚██╗ ██╔╝██╔═══██╗██╔════╝╚██╗ ██╔╝██╔════╝"
    echo "  ╚████╔╝ ██║   ██║███████╗ ╚████╔╝ ███████╗"
    echo "   ╚██╔╝  ██║   ██║╚════██║  ╚██╔╝  ╚════██║"
    echo "    ██║   ╚██████╔╝███████║   ██║   ███████║"
    echo "    ╚═╝    ╚═════╝ ╚══════╝   ╚═╝   ╚══════╝"
    echo -e "${NC}"
    echo -e "${DIM}Synthesis & Timing Analysis Tool${NC}"
    echo -e "${DIM}──────────────────────────────────────────────────────────${NC}"
    echo
}

show_status() {
    local status=$1
    local message=$2
    case $status in
        "info")    echo -e "${DIM}│ ${message}${NC}" >&2;;
        "success") echo -e "${GREEN}✔ ${message}${NC}" >&2;;
        "warning") echo -e "${YELLOW}│ ${message}${NC}" >&2;;
        "error")   echo -e "${RED}✖ ${message}${NC}" >&2;;
        *)         echo -e "${DIM}│ ${message}${NC}" >&2;;
    esac
}

select_module() {
    if [[ "$FZF" == "true" ]]; then
        # FZF mode
        echo -e "${DIM}◇ Select a module:${NC}" >&2
        
        local module_file=$(find "$BUILD_DIR" -type f \( -name "*.v" -o -name "*.sv" \) | sed "s|^$BUILD_DIR/||" | fzf --height=40% --prompt="Fuzzy Search: " --header="Use arrow keys to navigate, Enter to select")
        
        if [ -z "$module_file" ]; then
            echo -e "${RED}✖ No module selected. Exiting.${NC}" >&2
            exit 1
        fi
        
        echo -e "\033[1A\033[2K${GREEN}◆ Selected: $(basename "$module_file")${NC}" >&2
        echo "$module_file"
    else
        # Manual selection mode
        echo -e "${DIM}◇ Available modules:${NC}" >&2
        local module_files=()
        while IFS= read -r -d $'\0' file; do
            module_files+=("$file")
        done < <(find "$BUILD_DIR" -type f \( -name "*.sv" -o -name "*.v" \) -print0)

        if [ ${#module_files[@]} -eq 0 ]; then
            echo -e "${RED}✖ No module files found.${NC}" >&2
            exit 1
        fi

        for i in "${!module_files[@]}"; do
            echo -e "  ${GRAY}$((i+1)))${NC} $(basename "${module_files[$i]}")" >&2
        done

        local selected
        while true; do
            echo -ne "${YELLOW}? Select a module (1-${#module_files[@]}): ${NC}" >&2
            read -r selected
            if [[ "$selected" =~ ^[0-9]+$ ]] && \
               [ "$selected" -ge 1 ] && \
               [ "$selected" -le ${#module_files[@]} ]; then
                break
            elif [[ "$selected" = "q" || "$selected" = "Q" ]]; then
                echo -e "${RED}✖ Exiting.${NC}" >&2
                exit 0
            else
                echo -e "${RED}Invalid selection. Please enter a number between 1 and ${#module_files[@]}.${NC}" >&2
            fi
        done

        local module_file="${module_files[$((selected-1))]}"
        echo -e "\033[1A\033[2K${GREEN}◆ Selected: $(basename "$module_file")${NC}" >&2
        echo "$(basename "$module_file")"
    fi
}

fetch_top_module() {
    local flag=0
    echo -ne "${DIM}│ Enter the top module name: ${NC}" >&2
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

fetch_clock_period() {
    local default_period=10
    echo -ne "${DIM}│ Enter clock period in ns (default: ${default_period}ns = 100MHz): ${NC}" >&2
    read -r clock_period
    if [[ -z "$clock_period" ]]; then
        clock_period=$default_period
    fi
    show_status "success" "Clock period set to: ${clock_period}ns"
    echo "$clock_period"
}

run_sta() {
    show_header
    
    module_file="$(select_module)"
    top_module="$(fetch_top_module)"
    clock_period="$(fetch_clock_period)"
    
    mkdir -p "$SYNTH_DIR/yosys_${top_module}"
    cd "$SYNTH_DIR/yosys_${top_module}" || exit 1
    
    show_status "info" "Generating optimized synthesis script..."
    
    local clock_period_ps=$((clock_period * 1000))
    
    cat > "synth_sta.ys" << EOF
# Read design
read_verilog -sv $BUILD_DIR/${module_file}

# Hierarchy check
hierarchy -check -top ${top_module}

# High-level synthesis optimizations
proc; opt; fsm; opt; memory; opt

# Technology mapping for Xilinx 7-series
synth_xilinx -family xc7 -top ${top_module} -flatten -abc9 -nobram -nodsp

# Apply timing constraints
# Clock period: ${clock_period}ns = ${clock_period_ps}ps
select -module ${top_module}
setattr -set sta_clock_period ${clock_period_ps} w:clock
select -clear

# Static Timing Analysis (single run to avoid "No timing paths" warning)
tee -o timing_report.txt sta

# Design quality checks
scc

# Generate statistics
stat
stat -tech xilinx
stat -width

# Clean up before writing outputs
clean
opt_clean

# Write output files
write_verilog -noattr synth_${top_module}.v

# Print final stats
stat
EOF

    show_status "info" "Running Yosys synthesis and timing analysis..."
    
    if yosys -s "synth_sta.ys" > "synthesis.log" 2>&1; then
        show_status "success" "Synthesis completed successfully"
    else
        show_status "error" "Synthesis failed - check synthesis.log"
        exit 1
    fi

    echo -e "${CYAN}◆ Logic Loop Detection (SCC)${NC}"
    
    if [ -f synthesis.log ]; then
        local scc_found=$(grep "Found [0-9]* SCCs in module" synthesis.log | tail -1)
        local total_sccs=$(grep "^Found [0-9]* SCCs\.$" synthesis.log | tail -1 | awk '{print $2}')
        
        if [ ! -z "$total_sccs" ]; then
            if [ "$total_sccs" -eq 0 ]; then
                echo -e "${DIM}│${NC} ${GREEN}✔ No combinational loops detected${NC}"
            else
                echo -e "${DIM}│${NC} ${RED}✖ Found $total_sccs strongly connected components${NC}"
                echo -e "${DIM}│${NC} ${YELLOW}  (Indicates potential combinational loops)${NC}"
            fi
            
            if [ ! -z "$scc_found" ]; then
                echo -e "${DIM}│${NC} ${DIM}  $scc_found${NC}"
            fi
        else
            echo -e "${DIM}│${NC} ${YELLOW}SCC analysis not found in log${NC}"
        fi
    fi
    
    echo -e "${CYAN}◆ Resource Utilization${NC}"
    
    echo -e "${DIM}│${NC} ${YELLOW}Logic:${NC}"
    local total_luts=0
    for i in {2..6}; do
        local line=$(grep "LUT$i" synthesis.log | grep -v "LUTRAM" | tail -1)
        local count=$(grep -E "^\s*[0-9]+\s+LUT${i}\s*$" synthesis.log | tail -1 | awk '{print $1}')
        printf "${DIM}│${NC}   %-22s %d\n" "LUT$i:" "$count"
        total_luts=$((total_luts + count))
    done
    echo -e "${DIM}│${NC}   ──────────────────────────"
    printf "${DIM}│${NC}   %-22s %d\n" "Total LUTs:" "$total_luts"

    echo -e "${DIM}│${NC} ${YELLOW}Flip-Flops:${NC}"
    local count_fdre=$(grep -E "^\s*[0-9]+\s+FDRE\s*$" synthesis.log | tail -1 | awk '{print $1}')
    local count_fdse=$(grep -E "^\s*[0-9]+\s+FDSE\s*$" synthesis.log | tail -1 | awk '{print $1}')
    local count_fdce=$(grep -E "^\s*[0-9]+\s+FDCE\s*$" synthesis.log | tail -1 | awk '{print $1}')
    printf "${DIM}│${NC}   %-22s %d\n" "FDRE:" "${count_fdre:-0}"
    printf "${DIM}│${NC}   %-22s %d\n" "FDSE:" "${count_fdse:-0}"
    printf "${DIM}│${NC}   %-22s %d\n" "FDCE:" "${count_fdce:-0}"
    echo -e "${DIM}│${NC}   ──────────────────────────"
    local total_dffs=$(( ${count_fdre:-0} + ${count_fdse:-0} + ${count_fdce:-0} ))
    printf "${DIM}│${NC}   %-22s %d\n" "Total DFFs:" "$total_dffs"

    echo -e "${DIM}│${NC} ${YELLOW}IO:${NC}"
    local count_ibuf=$(grep -E "^\s*[0-9]+\s+IBUF\s*$" synthesis.log | tail -1 | awk '{print $1}')
    local count_obuf=$(grep -E "^\s*[0-9]+\s+OBUF\s*$" synthesis.log | tail -1 | awk '{print $1}')
    printf "${DIM}│${NC}   %-22s %d\n" "IBUF:" "${count_ibuf:-0}"
    printf "${DIM}│${NC}   %-22s %d\n" "OBUF:" "${count_obuf:-0}"
    echo -e "${DIM}│${NC}   ──────────────────────────"

    echo -e "${DIM}│${NC} ${YELLOW}Clock & Other:${NC}"
    local count_bufg=$(grep -E "^\s*[0-9]+\s+BUFG\s*$" synthesis.log | tail -1 | awk '{print $1}')
    local count_carry=$(grep -E "^\s*[0-9]+\s+CARRY4\s*$" synthesis.log | tail -1 | awk '{print $1}')
    printf "${DIM}│${NC}   %-22s %d\n" "BUFG (Clock):" "${count_bufg:-0}"
    printf "${DIM}│${NC}   %-22s %d\n" "CARRY4:" "${count_carry:-0}"
    echo -e "${DIM}│${NC}   ──────────────────────────"

    local estimated_lc=$(grep "Estimated number of LCs" synthesis.log | tail -1 | awk '{print $6}')
    if [ ! -z "$estimated_lc" ]; then
        echo -e "${DIM}│${NC}"
        echo -e "${DIM}│${NC} ${YELLOW}Estimated Logic Cells:${NC} $estimated_lc"
    fi

    echo -e "${CYAN}◆ Timing Analysis Results${NC}"
    
    if [ -f timing_report.txt ]; then
        local max_delay=$(grep "Latest arrival time in" timing_report.txt | sed -n "s/.*is \([0-9]*\):.*/\1/p" | head -1)
        
        if [ ! -z "$max_delay" ] && [ "$max_delay" -gt "0" ] 2>/dev/null; then
            local fmax_mhz=$(awk "BEGIN {printf \"%.2f\", 1000000 / $max_delay}")
            local target_mhz=$(awk "BEGIN {printf \"%.2f\", 1000 / $clock_period}")
            local period_ns=$(awk "BEGIN {printf \"%.3f\", $max_delay / 1000}")
            
            echo -e "${DIM}│${NC} ${YELLOW}Critical Path Delay:${NC}   ${max_delay} ps (${period_ns} ns)"
            echo -e "${DIM}│${NC} ${YELLOW}Achievable Fmax:${NC}      ${fmax_mhz} MHz"
            echo -e "${DIM}│${NC} ${YELLOW}Target Frequency:${NC}     ${target_mhz} MHz (${clock_period}ns period)"
            echo -e "${DIM}│${NC}"
            
            if (( $(echo "$fmax_mhz >= $target_mhz" | bc -l) )); then
                local slack=$(awk "BEGIN {printf \"%.1f\", ($fmax_mhz - $target_mhz) / $target_mhz * 100}")
                echo -e "${DIM}│${NC} ${GREEN}✔ Timing constraints MET (${slack}% margin)${NC}"
            else
                local deficit=$(awk "BEGIN {printf \"%.1f\", ($target_mhz - $fmax_mhz) / $target_mhz * 100}")
                echo -e "${DIM}│${NC} ${RED}✖ Timing constraints VIOLATED (${deficit}% deficit)${NC}"
            fi

        else
            echo -e "${DIM}│${NC} ${RED}Could not parse timing data${NC}"
            echo -e "${DIM}│${NC} ${YELLOW}Debug: max_delay='$max_delay'${NC}"
            echo -e "${DIM}│${NC} ${YELLOW}First few lines of timing_report.txt:${NC}"
            head -3 timing_report.txt | sed "s/^/${DIM}│${NC} /"
        fi
    else
        echo -e "${DIM}│${NC} ${RED}Timing report not generated${NC}"
    fi
    
    if [ -f timing_report.txt ]; then
        local max_delay=$(grep "Latest arrival time in" timing_report.txt | sed -n "s/.*is \([0-9]*\):.*/\1/p" | head -1)
        if [ ! -z "$max_delay" ] && [ "$max_delay" -gt "0" ] 2>/dev/null; then
            local fmax_mhz=$(awk "BEGIN {printf \"%.2f\", 1000000 / $max_delay}")
            
            echo -e "${CYAN}◆ Frequency Targets${NC}"
            
            for target in 50 100 125 150 200 250 300 400 500; do
                if (( $(echo "$fmax_mhz >= $target" | bc -l) )); then
                    local margin=$(awk "BEGIN {printf \"%.1f\", ($fmax_mhz - $target) / $target * 100}")
                    echo -e "${DIM}│${NC} ${GREEN}✔${NC} ${target} MHz (${margin}% timing margin)"
                else
                    local deficit=$(awk "BEGIN {printf \"%.1f\", ($target - $fmax_mhz) / $target * 100}")
                    echo -e "${DIM}│${NC} ${RED}✖${NC} ${target} MHz (${deficit}% too fast)"
                fi
            done
        fi
    fi

    echo -e "${GREEN}◆ Files Generated${NC}"
    echo -e "${DIM}├─${NC} synthesis.log            (Full synthesis log)"
    echo -e "${DIM}├─${NC} timing_report.txt        (Timing analysis)"
    echo -e "${DIM}├─${NC} synth_${top_module}.v       (Synthesized netlist)"
    echo -e "${DIM}└─${NC} synth_sta.ys            (Yosys script)"
    echo
    
    show_status "success" "All outputs saved in: $SYNTH_DIR/${top_module}/"

    echo ""
    echo "╔════════════════════════════════════════════════════════════════════╗"
    echo "║ WARNING: Yosys STA results are OPTIMISTIC                          ║"
    echo "║                                                                    ║"
    echo "║ Yosys STA assumes:                                                 ║"
    echo "║   • Zero routing delay                                             ║"
    echo "║   • Ideal placement                                                ║"
    echo "║   • Generic delay models (not Xilinx 7-series specific)            ║"
    echo "║                                                                    ║"
    echo "║ Expected Vivado results: 1.5x - 3.0x slower than Yosys estimate    ║"
    echo "║                                                                    ║"
    echo "║ For accurate timing, use Vivado synthesis + STA!                   ║"
    echo "╚════════════════════════════════════════════════════════════════════╝"
    echo ""
}

# Main execution
run_sta
