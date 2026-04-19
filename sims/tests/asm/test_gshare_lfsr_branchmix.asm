.globl _start

# ---------------------------------------------------
# GShare Negative Case: LFSR Branch Mix
# Goal:
#   Similar to pseudo-random stress, but with more than one conditional
#   branch per iteration so the GHR is filled with noisy, low-correlation
#   outcomes.
# Why it is hard:
#   - Low predictability
#   - Multiple branch sites update history every round
# ---------------------------------------------------

_start:
    li x10, 0
    li x11, 220
    li x12, 0xACE1     # 16-bit lfsr seed
    li x13, 0

loop:
    beq x10, x11, done

    andi x14, x12, 1
    srli x12, x12, 1
    beq x14, x0, skip_tap
    li x15, 0xB400
    xor x12, x12, x15

skip_tap:
    # Branch A: low bit
    andi x16, x12, 1
    beq x16, x0, a_taken
a_nt:
    addi x13, x13, 1
    j branch_b
a_taken:
    addi x13, x13, 2

branch_b:
    # Branch B: another shifted bit
    andi x17, x12, 8
    bne x17, x0, b_taken
b_nt:
    addi x13, x13, 3
    j loop_inc
b_taken:
    addi x13, x13, 4

loop_inc:
    addi x10, x10, 1
    j loop

done:
    li x20, 0x12345678
    ebreak
