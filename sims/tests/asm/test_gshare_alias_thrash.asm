.globl _start

# ---------------------------------------------------
# GShare Negative Case: PHT/BTB Alias Thrash
# Goal:
#   Create several static branches with the same low PC bits so they
#   are much more likely to contend for the same predictor entries.
# Pattern:
#   - Branch A: mostly taken
#   - Branch B: mostly not taken
#   - Branch C: alternating
# Why it is hard:
#   - GShare/PHT learns one branch, then another branch with the same
#     index footprint overwrites that state.
# ---------------------------------------------------

_start:
    li x10, 0          # iteration
    li x11, 192        # total rounds
    li x12, 0          # dummy accumulator

main_loop:
    beq x10, x11, done

    # Branch A at one alias location: TTTN (mostly taken)
    andi x13, x10, 3
    li x14, 3
    bne x13, x14, branch_a_taken
branch_a_nt:
    addi x12, x12, 1
    j branch_b_entry

    .balign 1024
branch_a_taken:
    addi x12, x12, 2

branch_b_entry:
    # Branch B at another alias location: mostly not taken
    andi x15, x10, 3
    beq x15, x0, branch_b_taken
branch_b_nt:
    addi x12, x12, 3
    j branch_c_entry

    .balign 1024
branch_b_taken:
    addi x12, x12, 4

branch_c_entry:
    # Branch C at yet another alias location: TNTN
    andi x16, x10, 1
    beq x16, x0, branch_c_taken
branch_c_nt:
    addi x12, x12, 5
    j loop_inc

    .balign 1024
branch_c_taken:
    addi x12, x12, 6

loop_inc:
    addi x10, x10, 1
    j main_loop

done:
    li x20, 0x12345678
    ebreak
