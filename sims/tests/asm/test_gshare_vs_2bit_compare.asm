.globl _start

# ---------------------------------------------------
# GShare vs 2-bit Saturating Counter Comparison Case
# Goal:
#   Build two nearby hot branches for horizontal comparison.
#
# Branch A:
#   Alternates strictly T/N/T/N by iteration parity.
#
# Branch B:
#   Appears immediately after Branch A and mirrors Branch A's outcome.
#
# Why this helps:
#   - A plain per-branch 2-bit counter tends to struggle on both branches
#     because each branch, viewed locally, still alternates.
#   - GShare can use recent global history, so Branch B becomes much easier
#     once Branch A's outcome is part of the history stream.
#
# Expected final architectural state after 256 iterations:
#   x12 = 128   # branch A taken count
#   x13 = 128   # branch A not-taken count
#   x14 = 128   # branch B taken count
#   x15 = 128   # branch B not-taken count
#   x16 = 384   # weighted score: 1*A_taken + 2*B_taken
#   x20 = 0x12345678
# ---------------------------------------------------

_start:
    li x10, 0          # i
    li x11, 256        # iterations
    li x12, 0          # branch A taken count
    li x13, 0          # branch A not-taken count
    li x14, 0          # branch B taken count
    li x15, 0          # branch B not-taken count
    li x16, 0          # weighted score

loop:
    andi x17, x10, 1
    beq x17, x0, branch_a_taken

branch_a_not_taken:
    addi x13, x13, 1
    bne x17, x0, branch_b_not_taken
    j branch_b_taken

branch_a_taken:
    addi x12, x12, 1
    addi x16, x16, 1
    beq x17, x0, branch_b_taken
    j branch_b_not_taken

branch_b_taken:
    addi x14, x14, 1
    addi x16, x16, 2
    j loop_inc

branch_b_not_taken:
    addi x15, x15, 1

loop_inc:
    addi x10, x10, 1
    blt x10, x11, loop

done:
    li x20, 0x12345678
    ebreak
