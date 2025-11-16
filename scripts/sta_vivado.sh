#!/bin/bash

BASE_DIR=$(dirname $(cd "$(dirname "$0")" && pwd))
BUILD_DIR=$BASE_DIR/build
SYNTH_DIR=$BASE_DIR/synth

# Colors
RED='\033[1;31m'
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
BLUE='\033[1;34m'
CYAN='\033[1;36m'
GRAY='\033[1;37m'
NC='\033[0m'
BOLD='\033[1m'
DIM='\033[2m'

show_header() {
    echo -e "${BLUE}"
    echo " ██╗   ██╗██╗██╗   ██╗ █████╗ ██████╗  ██████╗ "
    echo " ██║   ██║██║██║   ██║██╔══██╗██╔══██╗██╔═══██╗"
    echo " ██║   ██║██║██║   ██║███████║██║  ██║██║   ██║"
    echo " ╚██╗ ██╔╝██║╚██╗ ██╔╝██╔══██║██║  ██║██║   ██║"
    echo "  ╚████╔╝ ██║ ╚████╔╝ ██║  ██║██████╔╝╚██████╔╝"
    echo "   ╚═══╝  ╚═╝  ╚═══╝  ╚═╝  ╚═╝╚═════╝  ╚═════╝ "
    echo -e "${NC}"
    echo -e "${DIM}Vivado Static Timing Analysis Tool${NC}"
    echo -e "${DIM}────────────────────────────────────────────────${NC}"
    echo
}

show_status() {
    local status=$1
    local message=$2
    case $status in
        "info")    echo -e "${DIM}│ ${message}${NC}" >&2;;
        "success") echo -e "${GREEN}✔ ${message}${NC}" >&2;;
        "warning") echo -e "${YELLOW}⚠ ${message}${NC}" >&2;;
        "error")   echo -e "${RED}✖ ${message}${NC}" >&2;;
        *)         echo -e "${DIM}│ ${message}${NC}" >&2;;
    esac
}

select_module() {
    if [[ "$FZF" == "true" ]]; then
        echo -e "${DIM}◇ Select a module:${NC}" >&2
        
        local module_file=$(find "$BUILD_DIR" -type f \( -name "*.v" -o -name "*.sv" \) | sed "s|^$BUILD_DIR/||" | fzf --height=40% --prompt="Fuzzy Search: " --header="Use arrow keys to navigate, Enter to select")
        
        if [ -z "$module_file" ]; then
            echo -e "${RED}✖ No module selected. Exiting.${NC}" >&2
            exit 1
        fi
        
        echo -e "\033[1A\033[2K${GREEN}◆ Selected: $(basename "$module_file")${NC}" >&2
        echo "$module_file"
    else
        echo -e "${DIM}◇ Available modules:${NC}" >&2
        local module_files=()
        while IFS= read -r -d $'\0' file; do
            local rel_path="${file#$BUILD_DIR/}"
            module_files+=("$rel_path")
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
        echo "$module_file"
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

fetch_clock_port() {
    local default_port="clk"
    echo -ne "${DIM}│ Enter clock port name (default: ${default_port}): ${NC}" >&2
    read -r clock_port
    if [[ -z "$clock_port" ]]; then
        clock_port=$default_port
    fi
    show_status "success" "Clock port set to: ${clock_port}"
    echo "$clock_port"
}

fetch_fpga_part() {
    local default_part="xc7a100tcsg324-1"
    echo -ne "${DIM}│ Enter FPGA part (default: ${default_part}): ${NC}" >&2
    read -r fpga_part
    if [[ -z "$fpga_part" ]]; then
        fpga_part=$default_part
    fi
    show_status "success" "FPGA part set to: ${fpga_part}"
    echo "$fpga_part"
}

run_sta() {
    show_header
    
    module_file="$(select_module)"
    top_module="$(fetch_top_module)"
    clock_period="$(fetch_clock_period)"
    clock_port="$(fetch_clock_port)"
    fpga_part="$(fetch_fpga_part)"
    
    target_freq=$(awk "BEGIN {printf \"%.2f\", 1000.0 / $clock_period}")
    
    mkdir -p "$SYNTH_DIR/vivado_${top_module}"
    cd "$SYNTH_DIR/vivado_${top_module}" || exit 1
    
    relative_design_file="../../build/${module_file}"
    
    show_status "info" "Generating Vivado TCL script..."
    show_status "info" "Design file (relative): $relative_design_file"
    
    cat > "sta_vivado.tcl" << EOF
set design_file "$relative_design_file"
set top_module "$top_module"
set fpga_part "$fpga_part"
set clock_period_ns $clock_period
set clock_port "$clock_port"

set output_dir "./reports"
set project_dir "./project"

proc log_info {msg} {
    puts "\[INFO\] \$msg"
}

proc log_success {msg} {
    puts "\[SUCCESS\] \$msg"
}

proc log_error {msg} {
    puts "\[ERROR\] \$msg"
}

proc log_section {title} {
    puts ""
    puts "  \$title"
}

log_section "Vivado Synthesis + Timing Analysis"

file mkdir \$output_dir
log_success "Created output directory: \$output_dir"

set target_freq_mhz [expr {1000.0 / \$clock_period_ns}]
log_info "Design File: \$design_file"
log_info "Top Module: \$top_module"
log_info "FPGA Part: \$fpga_part"
log_info "Target Clock: \${clock_period_ns}ns ([format "%.2f" \$target_freq_mhz] MHz)"
log_info "Clock Port: \$clock_port"

log_section "Step 1: Project Setup"

if {[file exists \$project_dir]} {
    file delete -force \$project_dir
}

create_project -force sta_project \$project_dir -part \$fpga_part
log_success "Created project"

if {[file exists \$design_file]} {
    add_files \$design_file
    set_property top \$top_module [current_fileset]
    log_success "Added design file and set top module"
} else {
    log_error "Design file not found: \$design_file"
    log_error "Current working directory: [pwd]"
    exit 1
}

log_section "Step 2: Read Design"

read_verilog \$design_file
log_success "Read design files"

log_section "Step 3: Timing Constraints"

set xdc_file "\$project_dir/timing_constraints.xdc"
set fp [open \$xdc_file w]
puts \$fp "# Auto-generated timing constraints"
puts \$fp "# Clock constraint"
puts \$fp "create_clock -period \$clock_period_ns -name sys_clk \[get_ports \$clock_port\]"
puts \$fp ""
puts \$fp "# Relax I/O timing for internal path analysis"
puts \$fp "set_input_delay -clock sys_clk 0 \[all_inputs\]"
puts \$fp "set_output_delay -clock sys_clk 0 \[all_outputs\]"
close \$fp

read_xdc \$xdc_file
log_success "Created and loaded timing constraints"

log_section "Step 4: Synthesis"

log_info "Running synthesis (this may take a few minutes)..."

synth_design \\
    -top \$top_module \\
    -part \$fpga_part \\
    -mode out_of_context \\
    -flatten_hierarchy rebuilt \\
    -keep_equivalent_registers \\
    -resource_sharing off \\
    -no_lc \\
    -shreg_min_size 5

log_success "Synthesis completed"

log_section "Step 5: Generating Comprehensive Reports"

log_info "Generating clock reports..."
report_clocks -file "\$output_dir/clocks.rpt"
report_clock_interaction -delay_type min_max -file "\$output_dir/clock_interaction.rpt"
report_clock_networks -file "\$output_dir/clock_networks.rpt"

log_info "Generating timing reports..."

check_timing -verbose -file "\$output_dir/check_timing.rpt"

report_timing_summary \\
    -delay_type max \\
    -check_timing_verbose \\
    -max_paths 10 \\
    -input_pins \\
    -routable_nets \\
    -file "\$output_dir/timing_summary.rpt"

report_timing \\
    -delay_type max \\
    -max_paths 50 \\
    -sort_by slack \\
    -path_type full \\
    -input_pins \\
    -routable_nets \\
    -file "\$output_dir/timing_setup_detail.rpt"

report_timing \\
    -delay_type min \\
    -max_paths 50 \\
    -sort_by slack \\
    -path_type full \\
    -input_pins \\
    -routable_nets \\
    -file "\$output_dir/timing_hold_detail.rpt"

report_design_analysis \\
    -timing \\
    -complexity \\
    -congestion \\
    -max_paths 20 \\
    -file "\$output_dir/design_analysis.rpt"

report_datasheet \\
    -file "\$output_dir/datasheet.rpt"

log_info "Generating utilization reports..."

report_utilization \\
    -hierarchical \\
    -hierarchical_depth 5 \\
    -file "\$output_dir/utilization_hierarchical.rpt"

report_utilization \\
    -file "\$output_dir/utilization_summary.rpt"

log_info "Generating power reports..."

report_power \\
    -file "\$output_dir/power_summary.rpt"

log_info "Generating methodology reports..."

report_methodology \\
    -file "\$output_dir/methodology.rpt"

report_drc \\
    -file "\$output_dir/drc.rpt"

log_info "Analyzing high fanout nets..."
report_high_fanout_nets \\
    -timing \\
    -load_types \\
    -max_nets 100 \\
    -file "\$output_dir/high_fanout_nets.rpt"

log_info "Analyzing control sets..."
report_control_sets \\
    -verbose \\
    -file "\$output_dir/control_sets.rpt"

log_info "Generating route status..."
report_route_status \\
    -file "\$output_dir/route_status.rpt"

log_info "Generating property summary..."
report_property \\
    -all \\
    [current_design] \\
    -file "\$output_dir/design_properties.rpt"

log_success "All reports generated successfully"

log_section "Analysis Complete"

puts ""
puts "  GENERATED REPORTS"
puts ""
puts "  Timing Reports:"
puts "    • timing_summary.rpt           - Overall timing summary"
puts "    • timing_setup_detail.rpt      - Setup timing paths (50 worst)"
puts "    • timing_hold_detail.rpt       - Hold timing paths (50 worst)"
puts "    • critical_path_detailed.rpt   - Critical path breakdown"
puts "    • check_timing.rpt             - Constraint validation"
puts "    • design_analysis.rpt          - Design complexity analysis"
puts "    • datasheet.rpt                - I/O timing datasheet"
puts ""
puts "  Clock Reports:"
puts "    • clocks.rpt                   - Clock definitions"
puts "    • clock_interaction.rpt        - Clock domain crossings"
puts "    • clock_networks.rpt           - Clock tree analysis"
puts ""
puts "  Utilization Reports:"
puts "    • utilization_summary.rpt      - Resource summary"
puts "    • utilization_hierarchical.rpt - Hierarchical breakdown"
puts ""
puts "  Power Reports:"
puts "    • power_summary.rpt            - Power estimation"
puts ""
puts "  Analysis Reports:"
puts "    • high_fanout_nets.rpt         - High fanout analysis"
puts "    • control_sets.rpt             - Control set analysis"
puts "    • methodology.rpt              - Design methodology checks"
puts "    • drc.rpt                      - Design rule checks"
puts "    • route_status.rpt             - Routing status"
puts "    • design_properties.rpt        - Design properties"
puts ""
puts ""
puts "All reports saved in: \$output_dir/"
puts ""

log_success "Vivado STA analysis complete!"
EOF

    show_status "success" "TCL script generated: sta_vivado.tcl"
    echo
    
    echo
    echo -e "${GREEN}◆ Files Generated${NC}"
    echo -e "${DIM}├─${NC} sta_vivado.tcl           (TCL script)"
    echo -e "${DIM}├─${NC} vivado_run.log           (Vivado output log)"
    echo -e "${DIM}└─${NC} reports/                 (All analysis reports)"
    echo
    
    show_status "success" "All outputs saved in: $SYNTH_DIR/vivado_${top_module}/"
    
    echo ""
    echo "╔════════════════════════════════════════════════════════════════════╗"
    echo "║ NOTE: Vivado Post-Synthesis Timing Results                         ║"
    echo "║                                                                    ║"
    echo "║ For final timing closure, run full Place & Route in Vivado!        ║"
    echo "╚════════════════════════════════════════════════════════════════════╝"
    echo ""

    echo "Run the following command to start Vivado manually:"
    echo -e "${CYAN}vivado -mode batch -source sta_vivado.tcl${NC}"
}

run_sta
