.globl _start

# ---------------------------------------------------
# GShare History-Window Case
# Goal: Use a period-8 pattern so multiple history bits matter.
# Branch pattern:
#   i % 8 == 0,1,2,3,6,7 -> taken
#   i % 8 == 4,5         -> not taken
# Expectation:
#   - A functioning global-history predictor should stabilize well.
# ---------------------------------------------------

_start:
    li x10, 0          # i
    li x11, 320        # iterations
    li x12, 0          # dummy accumulator

loop:
    andi x13, x10, 7
    li x14, 4
    beq x13, x14, not_taken_path
    li x14, 5
    beq x13, x14, not_taken_path

taken_path:
    addi x12, x12, 2
    addi x10, x10, 1
    blt x10, x11, loop

not_taken_path:
    addi x12, x12, 1
    addi x10, x10, 1
    blt x10, x11, loop

done:
    li x20, 0x12345678
    ebreak
