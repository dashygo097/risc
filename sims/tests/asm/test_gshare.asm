.globl _start

_start:
    # ---------------------------------------------------
    # Comprehensive GShare Test Case
    # Goal: Use purely conditional branches (b-type)
    # to train the GShare and BTB.
    # ---------------------------------------------------

    li x10, 0        # Outer loop counter (i)
    li x11, 50       # Outer loop limit (N = 50)
    li x12, 0        # Dummy accumulator

outer_loop:
    li x13, 0        # Inner loop counter (j)
    li x14, 4        # Inner loop limit (M = 4)

inner_loop:
    # Conditional Branch 1: (j < 2) -> Taken twice, Not-Taken twice
    li x15, 2
    blt x13, x15, j_less_than_2

    # Fall through (j >= 2)
    addi x12, x12, 1
    beq x0, x0, inner_loop_merge

j_less_than_2:
    addi x12, x12, 2

inner_loop_merge:
    # Conditional Branch 2: (j % 2 == 0) -> Alternating
    andi x15, x13, 1
    bne x15, x0, j_is_odd

    # Fall through (j is even)
    addi x12, x12, 3
    beq x0, x0, inner_loop_inc

j_is_odd:
    addi x12, x12, 4

inner_loop_inc:
    addi x13, x13, 1
    blt x13, x14, inner_loop     # Inner loop back-edge (Conditional)

    addi x10, x10, 1
    blt x10, x11, outer_loop     # Outer loop back-edge (Conditional)

    # End
    li x20, 0x12345678
    ebreak
