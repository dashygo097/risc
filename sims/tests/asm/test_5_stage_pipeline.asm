# Pipeline Hazard Test Suite for RV32I 5-stage Pipeline
# Tests: RAW, WAW, WAR, Load-Use, Branch, Control hazards

.section .text
.globl _start

_start:
    nop
    lui x10, 0x80000
    
#==============================================================================
# Test 1: RAW (Read After Write) Hazard - EX-EX Forwarding
#==============================================================================
test_raw_ex_ex:
    addi x1, x0, 10       # x1 = 10
    addi x2, x1, 5        # x2 = 15 (needs x1 from previous instruction)
    addi x3, x2, 3        # x3 = 18 (needs x2 from previous instruction)
    # Expected: x1=10, x2=15, x3=18
    
#==============================================================================
# Test 2: RAW Hazard - MEM-EX Forwarding
#==============================================================================
test_raw_mem_ex:
    addi x4, x0, 20       # x4 = 20
    addi x5, x0, 30       # x5 = 30 (independent)
    add x6, x4, x5        # x6 = 50 (needs x4 from 2 cycles ago)
    # Expected: x4=20, x5=30, x6=50

#==============================================================================
# Test 3: RAW Hazard - Multiple Dependencies
#==============================================================================
test_raw_multiple:
    addi x7, x0, 100      # x7 = 100
    addi x8, x7, 50       # x8 = 150 (depends on x7)
    add x9, x7, x8        # x9 = 250 (depends on both x7 and x8)
    sub x11, x9, x7       # x11 = 150 (depends on x9 and x7)
    # Expected: x7=100, x8=150, x9=250, x11=150

#==============================================================================
# Test 4: Load-Use Hazard (requires 1 cycle stall)
#==============================================================================
test_load_use:
    # Setup: store value to memory first
    addi x12, x0, 42      # x12 = 42
    sw x12, 0x100(x10)    # store 42 to memory[base+0x100]
    
    # Load-Use hazard test
    lw x13, 0x100(x10)    # x13 = load from memory (takes extra cycle)
    addi x14, x13, 8      # x14 = x13 + 8 (MUST STALL - load-use hazard)
    add x15, x14, x13     # x15 = x14 + x13
    # Expected: x13=42, x14=50, x15=92
    
#==============================================================================
# Test 5: Load-Use with Forwarding (no stall needed)
#==============================================================================
test_load_no_stall:
    addi x16, x0, 99      # x16 = 99
    sw x16, 0x104(x10)    # store to memory
    
    lw x17, 0x104(x10)    # x17 = load
    nop                   # insert nop to avoid load-use
    addi x18, x17, 1      # x18 = x17 + 1 (no stall, forwarding from MEM)
    # Expected: x17=99, x18=100

#==============================================================================
# Test 6: Back-to-back Loads (Load-Use chain)
#==============================================================================
test_load_chain:
    addi x19, x0, 111     
    addi x20, x0, 222
    sw x19, 0x108(x10)    # store 111
    sw x20, 0x10C(x10)    # store 222
    
    lw x21, 0x108(x10)    # load 111
    lw x22, 0x10C(x10)    # load 222 (independent)
    add x23, x21, x22     # x23 = 333 (both loads must complete)
    # Expected: x21=111, x22=222, x23=333

#==============================================================================
# Test 7: WAW (Write After Write) - Register Renaming Test
# Note: In a simple 5-stage pipeline without OoO, true WAW doesn't occur
# But we test sequential writes to same register
#==============================================================================
test_waw:
    addi x24, x0, 1       # x24 = 1
    addi x24, x0, 2       # x24 = 2 (overwrites)
    addi x24, x0, 3       # x24 = 3 (overwrites again)
    addi x25, x24, 7      # x25 = 10 (should get final value 3)
    # Expected: x24=3, x25=10

#==============================================================================
# Test 8: WAR (Write After Read) - Should not be an issue in in-order pipeline
# But test register reuse patterns
#==============================================================================
test_war:
    addi x26, x0, 50      # x26 = 50
    addi x27, x26, 10     # x27 = 60 (reads x26)
    addi x26, x0, 100     # x26 = 100 (writes x26 after read)
    add x28, x26, x27     # x28 = 160 (should use new x26=100)
    # Expected: x26=100, x27=60, x28=160

#==============================================================================
# Test 9: Control Hazard - Branch Not Taken
#==============================================================================
test_branch_not_taken:
    addi x31, x0, 10      # x31 = 10
    addi x1, x0, 20       # x1 = 20
    beq x31, x1, branch_target1  # not taken (10 != 20)
    addi x2, x0, 30       # x2 = 30 (should execute)
    addi x3, x0, 40       # x3 = 40 (should execute)
    j after_branch1
branch_target1:
    addi x2, x0, 99       # should NOT execute
    addi x3, x0, 99       # should NOT execute
after_branch1:
    # Expected: x31=10, x1=20, x2=30, x3=40

#==============================================================================
# Test 10: Control Hazard - Branch Taken
#==============================================================================
test_branch_taken:
    addi x4, x0, 15       # x4 = 15
    addi x5, x0, 15       # x5 = 15
    beq x4, x5, branch_target2  # taken (15 == 15)
    addi x6, x0, 88       # should NOT execute (branch taken)
    addi x7, x0, 88       # should NOT execute
branch_target2:
    addi x6, x0, 55       # x6 = 55 (should execute)
    addi x7, x0, 66       # x7 = 66 (should execute)
    # Expected: x4=15, x5=15, x6=55, x7=66

#==============================================================================
# Test 11: Branch with RAW Hazard
#==============================================================================
test_branch_raw:
    addi x29, x0, 25       # x29 = 25
    addi x30, x29, 25      # x30 = 50 (RAW on x29)
    beq x29, x30, skip1    # not taken (25 != 50), but needs forwarded values
    addi x31, x0, 1        # x31 = 1
skip1:
    bne x29, x30, skip2    # taken (25 != 50)
    addi x31, x0, 99       # should NOT execute
skip2:
    # Expected: x29=25, x30=50, x31=1

#==============================================================================
# Test 12: JAL/JALR Control Hazard
#==============================================================================
test_jal:
    jal x11, jal_target   # x11 = return address, jump to target
    addi x12, x0, 99      # should NOT execute (in delay slot)
    addi x13, x0, 99      # should NOT execute
jal_target:
    addi x12, x0, 77      # x12 = 77 (should execute)
    jalr x14, x11, 0      # return using saved address
    # Expected: x12=77, x11=return_addr

#==============================================================================
# Test 13: Load followed by Branch (Load-Branch hazard)
#==============================================================================
test_load_branch:
    addi x11, x0, 100
    sw x11, 0x110(x10)    # store 100
    
    lw x12, 0x110(x10)    # x12 = 100
    addi x13, x0, 100     # x13 = 100
    beq x12, x13, lb_target  # branch depends on loaded value (needs stall)
    addi x14, x0, 99      # should NOT execute
    j lb_after
lb_target:
    addi x14, x0, 200     # x14 = 200
lb_after:
    # Expected: x12=100, x13=100, x14=200

#==============================================================================
# Test 14: Store followed by Load (potential forwarding)
#==============================================================================
test_store_load:
    addi x15, x0, 123     # value
    sw x15, 0x120(x10)    # store 123
    lw x16, 0x120(x10)    # load same location (may need forwarding)
    addi x17, x16, 7      # x17 = 130
    # Expected: x15=123, x16=123, x17=130

#==============================================================================
# Test 15: Complex Dependency Chain
#==============================================================================
test_complex_chain:
    addi x18, x0, 1       # x18 = 1
    addi x19, x18, 2      # x19 = 3
    add x20, x18, x19     # x20 = 4
    sub x21, x20, x18     # x21 = 3
    and x22, x21, x19     # x22 = 3
    or x23, x22, x20      # x23 = 7
    xor x24, x23, x21     # x24 = 4
    # Long dependency chain testing all forwarding paths
    # Expected: x18=1, x19=3, x20=4, x21=3, x22=3, x23=7, x24=4

#==============================================================================
# Test 16: Load with multiple consumers
#==============================================================================
test_load_multiple_use:
    addi x1, x0, 0x130
    addi x2, x0, 50
    sw x2, 0(x1)
    
    lw x3, 0(x1)          # x3 = 50
    add x4, x3, x3        # x4 = 100 (first use)
    add x5, x3, x4        # x5 = 150 (second use)
    add x6, x3, x5        # x6 = 200 (third use)
    # Expected: x3=50, x4=100, x5=150, x6=200

#==============================================================================
# Test 17: Branch prediction flushing test
#==============================================================================
test_branch_flush:
    addi x25, x0, 5
    addi x26, x0, 5
    addi x27, x0, 1        # These should execute
    addi x28, x0, 2        # These should execute
    beq x25, x26, flush_target  # taken
    addi x27, x0, 99       # should be flushed
    addi x28, x0, 99       # should be flushed
    addi x29, x0, 99       # should be flushed
flush_target:
    # Expected: x27=1, x28=2 (not 99)

#==============================================================================
# Test 18: Back-to-back branches
#==============================================================================
test_double_branch:
    addi x29, x0, 10
    addi x30, x0, 20
    blt x29, x30, db1     # taken (10 < 20)
    addi x31, x0, 99
db1:
    addi x31, x0, 30      # x31 = 30
    bge x30, x29, db2     # taken (20 >= 10)
    addi x1, x0, 99
db2:
    addi x1, x0, 40       # x1 = 40
    # Expected: x31=30, x1=40

#==============================================================================
# Test Complete - Loop Forever
#==============================================================================
test_end:
    ebreak                # breakpoint for simulation
    j test_end            # infinite loop

.section .data
# Data section for memory operations
test_data:
    .word 0x12345678
    .word 0xDEADBEEF
