.section .text
.globl _start

_start:
    lui x10, 0x80000

    # AUIPC
    la x1, label1   # Load address of label1 into x1

label1:

    ebreak
