#!/bin/bash
cd /home/pilgrim/workplace/cpu/risc/sims
LOG=logs/demu_trace.log
RET=logs/demu_trace.retire.txt
BR=logs/demu_branch_flow.csv

echo "Running simulation..."
./build/bin/demu_rv32im -c 50000 -L2 tests/elf/test_gshare.elf > "$LOG" 2>&1

echo "Parsing logs..."
awk -F'\|' '/RETIRE/ {
  cyc=$2; gsub(/^ +| +$/,"",cyc); sub(/^Cycle[[:space:]]+/,"",cyc)
  pcf=$3; gsub(/^ +| +$/,"",pcf); sub(/^PC=0x/,"",pcf)
  inf=$4; gsub(/^ +| +$/,"",inf); sub(/^Inst=0x/,"",inf)
  print "Cycle " cyc " | PC=0x" pcf " | Inst=0x" inf
}' "$LOG" > "$RET"

awk -v objdump="tests/dump/test_gshare.asm.dump" -F'\|' '
function trim(s){ gsub(/^ +| +$/, "", s); return s }
BEGIN {
  print "cycle,pc,inst,mnemonic,next_pc,is_control_flow,is_taken"
  while ((getline line < objdump) > 0) {
    if (match(line, /^[[:space:]]*([0-9a-fA-F]+):[[:space:]]+([0-9a-fA-F]+)[[:space:]]+(.*)$/, arr)) {
      asm_map[arr[1]] = trim(arr[3])
    }
  }
  close(objdump)
  have=0
}
/RETIRE/ {
  cyc=trim($2); sub(/^Cycle[[:space:]]+/,"",cyc)
  pcf=trim($3); sub(/^PC=0x/,"",pcf)
  inf=trim($4); sub(/^Inst=0x/,"",inf)
  insthex=inf; sub(/ .*$/, "", insthex)
  
  asm = asm_map[pcf]
  if (asm == "") asm = "unknown"
  
  cur_cycle=cyc; cur_pc=pcf; cur_inst=insthex; cur_asm=asm
  if(have){
    is_cf = (prev_asm ~ /^(beq|bne|blt|bge|bltu|bgeu|jal|jalr)\b/) ? 1 : 0
    if(is_cf){
      expect = strtonum("0x" prev_pc) + 4
      got = strtonum("0x" cur_pc)
      taken = (got != expect) ? 1 : 0
      printf "%s,0x%s,0x%s,\"%s\",0x%s,%d,%d\n", prev_cycle, prev_pc, prev_inst, prev_asm, cur_pc, is_cf, taken
    }
  }
  prev_cycle=cur_cycle; prev_pc=cur_pc; prev_inst=cur_inst; prev_asm=cur_asm; have=1
}
END{
  if(have){
    is_cf = (prev_asm ~ /^(beq|bne|blt|bge|bltu|bgeu|jal|jalr)\b/) ? 1 : 0
    if(is_cf){
      printf "%s,0x%s,0x%s,\"%s\",NA,%d,NA\n", prev_cycle, prev_pc, prev_inst, prev_asm, is_cf
    }
  }
}' "$LOG" > "$BR"
echo "Done! Generated $LOG, $RET, $BR"
