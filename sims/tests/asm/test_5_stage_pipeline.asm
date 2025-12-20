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
    sub x10, x9, x7       # x10 = 150 (depends on x9 and x7)
    # Expected: x7=100, x8=150, x9=250, x10=150

#==============================================================================
# Test 4: Load-Use Hazard (requires 1 cycle stall)
#==============================================================================
test_load_use:
    # Setup: store value to memory first
    addi x11, x0, 0x100   # x11 = memory address 0x100
    addi x12, x0, 42      # x12 = 42
    sw x12, 0(x11)        # store 42 to memory[0x100]
    
    # Load-Use hazard test
    lw x13, 0(x11)        # x13 = load from memory (takes extra cycle)
    addi x14, x13, 8      # x14 = x13 + 8 (MUST STALL - load-use hazard)
    add x15, x14, x13     # x15 = x14 + x13
    # Expected: x13=42, x14=50, x15=92
    
#==============================================================================
# Test 5: Load-Use with Forwarding (no stall needed)
#==============================================================================
test_load_no_stall:
    addi x16, x0, 0x104   # x16 = address
    addi x17, x0, 99      # x17 = 99
    sw x17, 0(x16)        # store to memory
    
    lw x18, 0(x16)        # x18 = load
    nop                   # insert nop to avoid load-use
    addi x19, x18, 1      # x19 = x18 + 1 (no stall, forwarding from MEM)
    # Expected: x18=99, x19=100

#==============================================================================
# Test 6: Back-to-back Loads (Load-Use chain)
#==============================================================================
test_load_chain:
    addi x20, x0, 0x108   # base address
    addi x21, x0, 111     
    addi x22, x0, 222
    sw x21, 0(x20)        # store 111
    sw x22, 4(x20)        # store 222
    
    lw x23, 0(x20)        # load 111
    lw x24, 4(x20)        # load 222 (independent)
    add x25, x23, x24     # x25 = 333 (both loads must complete)
    # Expected: x23=111, x24=222, x25=333

#==============================================================================
# Test 7: WAW (Write After Write) - Register Renaming Test
# Note: In a simple 5-stage pipeline without OoO, true WAW doesn't occur
# But we test sequential writes to same register
#==============================================================================
test_waw:
    addi x26, x0, 1       # x26 = 1
    addi x26, x0, 2       # x26 = 2 (overwrites)
    addi x26, x0, 3       # x26 = 3 (overwrites again)
    addi x27, x26, 7      # x27 = 10 (should get final value 3)
    # Expected: x26=3, x27=10

#==============================================================================
# Test 8: WAR (Write After Read) - Should not be an issue in in-order pipeline
# But test register reuse patterns
#==============================================================================
test_war:
    addi x28, x0, 50      # x28 = 50
    addi x29, x28, 10     # x29 = 60 (reads x28)
    addi x28, x0, 100     # x28 = 100 (writes x28 after read)
    add x30, x28, x29     # x30 = 160 (should use new x28=100)
    # Expected: x28=100, x29=60, x30=160

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
    addi x8, x0, 25       # x8 = 25
    addi x9, x8, 25       # x9 = 50 (RAW on x8)
    beq x8, x9, skip1     # not taken (25 != 50), but needs forwarded values
    addi x10, x0, 1       # x10 = 1
skip1:
    bne x8, x9, skip2     # taken (25 != 50)
    addi x10, x0, 99      # should NOT execute
skip2:
    # Expected: x8=25, x9=50, x10=1

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
    addi x15, x0, 0x110   # address
    addi x16, x0, 100
    sw x16, 0(x15)        # store 100
    
    lw x17, 0(x15)        # x17 = 100
    addi x18, x0, 100     # x18 = 100
    beq x17, x18, lb_target  # branch depends on loaded value (needs stall)
    addi x19, x0, 99      # should NOT execute
    j lb_after
lb_target:
    addi x19, x0, 200     # x19 = 200
lb_after:
    # Expected: x17=100, x18=100, x19=200

#==============================================================================
# Test 14: Store followed by Load (potential forwarding)
#==============================================================================
test_store_load:
    addi x20, x0, 0x120   # address
    addi x21, x0, 123     # value
    sw x21, 0(x20)        # store 123
    lw x22, 0(x20)        # load same location (may need forwarding)
    addi x23, x22, 7      # x23 = 130
    # Expected: x21=123, x22=123, x23=130

#==============================================================================
# Test 15: Complex Dependency Chain
#==============================================================================
test_complex_chain:
    addi x24, x0, 1       # x24 = 1
    addi x25, x24, 2      # x25 = 3
    add x26, x24, x25     # x26 = 4
    sub x27, x26, x24     # x27 = 3
    and x28, x27, x25     # x28 = 3
    or x29, x28, x26      # x29 = 7
    xor x30, x29, x27     # x30 = 4
    # Long dependency chain testing all forwarding paths
    # Expected: x24=1, x25=3, x26=4, x27=3, x28=3, x29=7, x30=4

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
    addi x7, x0, 5
    addi x8, x0, 5
    addi x9, x0, 1        # These should execute
    addi x10, x0, 2       # These should execute
    beq x7, x8, flush_target  # taken
    addi x9, x0, 99       # should be flushed
    addi x10, x0, 99      # should be flushed
    addi x11, x0, 99      # should be flushed
flush_target:
    # Expected: x9=1, x10=2 (not 99)

#==============================================================================
# Test 18: Back-to-back branches
#==============================================================================
test_double_branch:
    addi x12, x0, 10
    addi x13, x0, 20
    blt x12, x13, db1     # taken (10 < 20)
    addi x14, x0, 99
db1:
    addi x14, x0, 30      # x14 = 30
    bge x13, x12, db2     # taken (20 >= 10)
    addi x15, x0, 99
db2:
    addi x15, x0, 40      # x15 = 40
    # Expected: x14=30, x15=40

#==============================================================================
# Test Complete - Loop Forever
#==============================================================================
test_end:
    ebreak              
    j test_end       

.section .data
# Data section for memory operations
test_data:
    .word 0x12345678
    .word 0xDEADBEEF
