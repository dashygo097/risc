.globl _start

# ---------------------------------------------------
# GShare Negative Case: Interleaved Independent Branches
# Goal:
#   Interleave several branches, each following its own local rule.
# Branch patterns:
#   - A depends on i % 2
#   - B depends on i % 3
#   - C depends on i % 5
# Why it is hard:
#   - Their outcomes are individually simple, but when interleaved they
#     pollute global history and reduce usefulness of a single shared GHR.
# ---------------------------------------------------

_start:
    li x10, 0          # i
    li x11, 240        # iterations
    li x12, 0

loop:
    beq x10, x11, done

    # Branch A: TNTN...
    andi x13, x10, 1
    beq x13, x0, a_taken
a_nt:
    addi x12, x12, 1
    j branch_b
a_taken:
    addi x12, x12, 2

branch_b:
    # Branch B: T N N T N N ...
    li x14, 3
    rem x15, x10, x14
    beq x15, x0, b_taken
b_nt:
    addi x12, x12, 3
    j branch_c
b_taken:
    addi x12, x12, 4

branch_c:
    # Branch C: taken once every five iterations
    li x16, 5
    rem x17, x10, x16
    beq x17, x0, c_taken
c_nt:
    addi x12, x12, 5
    j loop_inc
c_taken:
    addi x12, x12, 6

loop_inc:
    addi x10, x10, 1
    j loop

done:
    li x20, 0x12345678
    ebreak
