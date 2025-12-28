.section .text
.globl _start

_start:
    lui x10, 0x80000

    addi x1, x0, 0x12   # x1 = 0x12
    sw x1, 0(x10)         # mem[0x80000000] = 0x00000012
    
    addi x2, x0, 0x56   # x2 = 0x56
    sw x2, 4(x10)         # mem[0x80000004] = 0x00000056
    
    lw x3, 0(x10)         # x3 = 0x00000012
    lw x4, 4(x10)         # x4 = 0x00000056

    ebreak
