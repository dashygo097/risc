.globl _start

# ---------------------------------------------------
# GShare Negative Case: Long-Period Pattern
# Goal:
#   Drive one hot branch with a long period that is inconvenient for
#   short global-history correlation.
# Pattern:
#   taken only when i % 16 is 0, 3, 9, or 14
# Why it is hard:
#   - Sparse taken events
#   - Period longer than the short easy patterns
#   - Nearby control flow keeps injecting extra branch outcomes
# ---------------------------------------------------

_start:
    li x10, 0
    li x11, 320
    li x12, 0

loop:
    beq x10, x11, done

    andi x13, x10, 15

    li x14, 0
    beq x13, x14, hot_taken
    li x14, 3
    beq x13, x14, hot_taken
    li x14, 9
    beq x13, x14, hot_taken
    li x14, 14
    beq x13, x14, hot_taken

hot_nt:
    addi x12, x12, 1
    j loop_inc

hot_taken:
    addi x12, x12, 7

loop_inc:
    addi x10, x10, 1
    j loop

done:
    li x20, 0x12345678
    ebreak
