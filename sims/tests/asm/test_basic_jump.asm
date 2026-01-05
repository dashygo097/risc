.section .text
.globl _start

_start:
    lui x10, 0x80000

    # JAL 
    jal x1, label1      # Jump to label1, save PC+4 in x1
    addi x2, x0, 99     # Should NOT execute
    
label1:
    addi x3, x0, 1      # x3 = 1 (success)

    # JALR
    la x4, label2     # Load address of label2 into x4
    jalr x5, 0(x4)   # Jump to address in x4, do not save return address
    addi x6, x0, 99    # Should NOT execute

label2:
    addi x7, x0, 2     # x7 = 2 (success)
    
    ebreak
