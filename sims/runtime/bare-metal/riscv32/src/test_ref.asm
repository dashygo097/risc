.section .text.entry, "ax"
.globl main

main:
    la x10, __dmem_base 

    # sw/lw 
    addi x1, x0, 0x12   # x1 = 0x12
    sw x1, 0(x10)         # mem[0x80040000] = 0x00000012
    j target
    nop
    nop


target:
    lw x2, 0(x10)
    j .
