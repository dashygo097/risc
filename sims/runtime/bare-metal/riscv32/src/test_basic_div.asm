.section .text.entry, "ax"
.globl main

main:
    # Base operands
    li   x1, 20
    li   x2, 3
    li   x3, -20
    li   x4, -3
    li   x5, 0

    # -----------------------------
    # Signed divide/remainder
    # -----------------------------
    div  x10, x1, x2              # 20 / 3 = 6
    rem  x11, x1, x2              # 20 % 3 = 2

    div  x12, x3, x2              # -20 / 3 = -6 (toward zero)
    rem  x13, x3, x2              # -20 % 3 = -2

    div  x14, x1, x4              # 20 / -3 = -6
    rem  x15, x1, x4              # 20 % -3 = 2

    div  x16, x3, x4              # -20 / -3 = 6
    rem  x17, x3, x4              # -20 % -3 = -2

    # -----------------------------
    # Unsigned divide/remainder
    # -----------------------------
    li   x6, 0xffffffff
    li   x7, 5
    divu x18, x6, x7              # 0xffffffff / 5 = 0x33333333
    remu x19, x6, x7              # 0xffffffff % 5 = 0

    # -----------------------------
    # Divide by zero behavior (RISC-V spec)
    # -----------------------------
    div  x20, x1, x5              # = -1
    divu x21, x1, x5              # = 0xffffffff
    rem  x22, x1, x5              # = dividend (20)
    remu x23, x1, x5              # = dividend (20)

    # -----------------------------
    # Overflow case: INT_MIN / -1
    # -----------------------------
    li   x24, 0x80000000
    li   x25, -1
    div  x26, x24, x25            # = INT_MIN
    rem  x27, x24, x25            # = 0

    addi x30, x0, 0

    li   t3, 6
    bne  x10, t3, fail

    li   t3, 2
    bne  x11, t3, fail

    li   t3, -6
    bne  x12, t3, fail

    li   t3, -2
    bne  x13, t3, fail

    li   t3, -6
    bne  x14, t3, fail

    li   t3, 2
    bne  x15, t3, fail

    li   t3, 6
    bne  x16, t3, fail

    li   t3, -2
    bne  x17, t3, fail

    li   t3, 0x33333333
    bne  x18, t3, fail

    li   t3, 0
    bne  x19, t3, fail

    li   t3, -1
    bne  x20, t3, fail

    li   t3, 0xffffffff
    bne  x21, t3, fail

    li   t3, 20
    bne  x22, t3, fail

    li   t3, 20
    bne  x23, t3, fail

    li   t3, 0x80000000
    bne  x26, t3, fail

    li   t3, 0
    bne  x27, t3, fail

    j end

fail:
    addi x30, x30, 1
    j end

end:
    j .
