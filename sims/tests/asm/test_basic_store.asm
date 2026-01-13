.section .text
.globl _start

_start:
    lui x10, 0x80000

    # sw
    addi x1, x0, 0x12   # x1 = 0x12
    addi x2, x0, 0x34   # x2 = 0x34
    addi x3, x0, 0x56   # x3 = 0x56
    addi x4, x0, 0x78   # x4 = 0x78
    sw x1, 0(x10)      # mem[0x80000000] = 0x00000012
    sw x2, 4(x10)      # mem[0x80000004] = 0x00000034
    sw x3, 8(x10)      # mem[0x80000008] = 0x00000056
    sw x4, 12(x10)     # mem[0x8000000C] = 0x00000078


