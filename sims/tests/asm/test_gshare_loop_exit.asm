.globl _start

# ---------------------------------------------------
# GShare Loop Exit Case
# Goal: Isolate the classic "mostly taken, one final not-taken" branch.
# Expectation:
#   - This should be a very easy case.
#   - Misses should be limited to warm-up plus loop exit.
# ---------------------------------------------------

_start:
    li x10, 300        # countdown
    li x12, 0          # dummy accumulator

loop_body:
    addi x12, x12, 1
    addi x10, x10, -1
    bne x10, x0, loop_body

done:
    li x20, 0x12345678
    ebreak
