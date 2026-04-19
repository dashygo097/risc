.globl _start

# ---------------------------------------------------
# GShare Alternating Case
# Goal: Stress a single hot branch with a strict T/N/T/N pattern.
# Expectation:
#   - A plain 2-bit counter struggles.
#   - A working GShare should learn the parity-correlated history.
# ---------------------------------------------------

_start:
    li x10, 0          # i
    li x11, 256        # iterations
    li x12, 0          # dummy accumulator

loop:
    andi x13, x10, 1
    beq x13, x0, even_path   # taken on even iterations

odd_path:
    addi x12, x12, 1
    addi x10, x10, 1
    blt x10, x11, loop

even_path:
    addi x12, x12, 2
    addi x10, x10, 1
    blt x10, x11, loop

done:
    li x20, 0x12345678
    ebreak
