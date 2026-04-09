.section .text.entry, "ax"
.globl main

main:
    la x10, __dmem_base 

    # AUIPC
    la x1, label1   # Load address of label1 into x1

label1:
    j .
