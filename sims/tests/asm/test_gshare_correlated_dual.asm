.globl _start

# ---------------------------------------------------
# GShare Correlated Dual-Branch Case
# Goal: Keep two nearby conditional branches active in each iteration.
# Branch A follows T/T/N/N.
# Branch B follows T/N/T/N.
# Expectation:
#   - Good for spotting history pollution between adjacent branches.
# ---------------------------------------------------

_start:
    li x10, 0          # i
    li x11, 256        # iterations
    li x12, 0          # accumulator

loop:
    andi x13, x10, 3
    li x14, 2
    blt x13, x14, branch_a_taken

branch_a_fallthrough:
    addi x12, x12, 1
    j branch_b_prep

branch_a_taken:
    addi x12, x12, 2

branch_b_prep:
    andi x15, x10, 1
    beq x15, x0, branch_b_taken

branch_b_fallthrough:
    addi x12, x12, 3
    j loop_inc

branch_b_taken:
    addi x12, x12, 4

loop_inc:
    addi x10, x10, 1
    blt x10, x11, loop

done:
    li x20, 0x12345678
    ebreak
