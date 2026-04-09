.section .text.entry, "ax"
.globl main

main:
    # base address for optional stores
    la x20, __dmem_base 

    addi x1,  x0, 3
    addi x2,  x0, 5
    addi x3,  x0, -7
    addi x4,  x0, 9
    li   x5,  0x7fffffff
    li   x6,  0xffffffff

    mul    x10, x1, x2            # 3 * 5 = 15
    mul    x11, x3, x4            # -7 * 9 = -63
    mul    x12, x5, x2            # 0x7fffffff * 5 = 0x17ffffffb -> low = 0x7ffffffb
    mulh   x13, x3, x4            # high signed of -7 * 9 = high(-63) = -1
    mulhu  x14, x6, x2            # high unsigned of 0xffffffff * 5 = 0x4fffffffb -> high = 4
    mul    x15, x5, x5            # (2^31-1)^2 -> low = 0x00000001

    mul    x16, x2, x4            # 5 * 9 = 45
    add    x17, x16, x0           # consume immediately
    sw     x17, 0(x20)            # store result
    lw     x18, 0(x20)            # load back

    addi   x7, x0, 12
    addi   x8, x0, -3
    mul    x19, x7, x8            # 12 * -3 = -36
    mulh   x21, x7, x8            # high signed part of -36 = -1


    mul    x24, x10, x1           # 15 * 3 = 45
    add    x25, x24, x2           # 45 + 5 = 50
    mul    x26, x25, x3           # 50 * -7 = -350


    addi x30, x0, 0

    li t3, 15
    bne x10, t3, fail

    li t3, -63
    bne x11, t3, fail

    li t3, 0x7ffffffb
    bne x12, t3, fail

    li t3, -1
    bne x13, t3, fail

    li t3, 4
    bne x14, t3, fail

    li t3, 1
    bne x15, t3, fail

    li t3, 45
    bne x18, t3, fail

    li t3, -36
    bne x19, t3, fail

    li t3, -1
    bne x21, t3, fail

    li t3, 45
    bne x24, t3, fail

    li t3, 50
    bne x25, t3, fail

    li t3, -350
    bne x26, t3, fail

    j end

fail:
    addi x30, x30, 1
    j end

end:
    j .
