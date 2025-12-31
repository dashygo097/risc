.section .text
.globl _start

_start:
    lui x10, 0x80000

    # sw/lw 
    addi x1, x0, 0x12   # x1 = 0x12
    sw x1, 0(x10)         # mem[0x80000000] = 0x00000012
    
    addi x2, x0, 0x56   # x2 = 0x56
    sw x2, 4(x10)         # mem[0x80000004] = 0x00000056
    
    lw x3, 0(x10)         # x3 = 0x00000012
    lw x4, 4(x10)         # x4 = 0x00000056

    # sh/sb/lh/lb
    lui x5, 0x01234
    ori x5, x5, 0x567

    sh x5, 8(x10)         # mem[0x80000008] = 0x00004567
    lh x6, 8(x10)         # x6 = 0x00004567

    sb x5, 12(x10)        # mem[0x8000000C] = 0x000067
    lb x7, 12(x10)        # x7 = 0x000067

    # lh/lb -- sign-extended
    addi x8, x0, -16   # x8 = 0xFFFFFFF0

    sh x8, 16(x10)        # mem[0x80000010] = 0x0000FFF0
    lh x9, 16(x10)        # x9 = 0xFFFFFFF0 (sign-extended)

    sb x8, 20(x10)        # mem[0x80000014] = 0x000000F0
    lb x11, 20(x10)       # x11 = 0xFFFFFFF0 (sign-extended)

    # lhu/lbu
    lhu x12, 16(x10)       # x12 = 0x0000FFF0 (zero-extended)
    lbu x13, 20(x10)       # x13 = 0x000000F0 (zero-extended)


    ebreak
