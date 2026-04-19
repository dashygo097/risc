.globl _start

# ---------------------------------------------------
# GShare Bad Case: Pseudo-Random Branching (LFSR)
# Goal: Create a sequence of branches that are mathematically
#       hard to predict based on simple history.
#
# This uses a 32-bit LFSR (Linear Feedback Shift Register)
# to generate a pseudo-random bit stream.
#
# Expected Outcome: High misprediction rate, lower IPC.
# ---------------------------------------------------

_start:
    li x10, 0          # Loop counter (i)
    li x11, 2000       # Loop limit
    li x12, 0xACE1     # LFSR State (Seed), non-zero

loop_random:
    beq x10, x11, loop_end

    # ---------------------------------------------------
    # LFSR Step: x12 = (x12 >> 1) ^ (-(x12 & 1u) & 0xB400u);
    # Polynomial: x^16 + x^14 + x^13 + x^11 + 1 (period 65535)
    # ---------------------------------------------------

    andi x13, x12, 1        # x13 = lsb(lfsr)
    srli x12, x12, 1        # lfsr >>= 1

    beqz x13, check_branch  # If lsb was 0, skip XOR

    li x14, 0xB400          # Polynomial taps mask
    xor x12, x12, x14       # lfsr ^= mask

check_branch:
    # Branch based on the LSB of the NEW state
    andi x15, x12, 1
    beqz x15, path_taken    # 50% chance

path_not_taken:
    addi x5, x5, 1          # Dummy work
    j loop_inc

path_taken:
    addi x6, x6, 1          # Dummy work

loop_inc:
    addi x10, x10, 1
    j loop_random

loop_end:
    li x20, 0x12345678
    ebreak
