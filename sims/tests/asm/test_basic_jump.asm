.section .text
.globl _start

_start:
    lui x10, 0x80000

    # JAL 
    jal x1, label1      # Jump to label1, save PC+4 in x1
    addi x2, x0, 99     # Should NOT execute
    
label1:
    addi x3, x0, 1      # x3 = 1 (success)
    
    ebreak
