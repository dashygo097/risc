.globl _start

# ---------------------------------------------------
# GShare Good Case: Repeating Branch Pattern
# Goal: Test GShare's ability to learn a repeating 4-step pattern:
#       Take, Take, Take, Not Taken (TTTN)
#
# Since GShare uses the global history (last 10 branches), it should
# easily identify this pattern after a short warm-up.
# ---------------------------------------------------

_start:
    li x10, 0          # Loop counter (i)
    li x11, 2000       # Loop limit (N = 2000)
    li x12, 0          # Event counter (debug)

    # We will use "i % 4" logic.
    # 0 -> T
    # 1 -> T
    # 2 -> T
    # 3 -> N

loop_pattern:
    beq x10, x11, loop_end  # Loop limit check

    andi x13, x10, 3   # x13 = i % 4

    # Branch logic:
    # if (x13 == 3) -> Not Taken (fall through)
    # else          -> Taken (jump)

    li x14, 3
    beq x13, x14, path_not_taken  # The pattern breaker (N)

path_taken:
    # Logic for the Taken path (T)
    # Just do some dummy work
    addi x12, x12, 1
    j loop_inc

path_not_taken:
    # Logic for the Not Taken path (N)
    addi x12, x12, 10

loop_inc:
    addi x10, x10, 1   # i++
    j loop_pattern

loop_end:
    li x20, 0x12345678
    ebreak
