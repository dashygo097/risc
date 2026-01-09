.section .text
.globl _start

_start:
    lui x10, 0x80000

    addi x2, x0, 0      # Initialize x2 to 0
    addi x5, x0, 0      # Initialize x5 to 0

    # JAL 
    jal x1, label1      # Jump to label1, save PC+4 in x1
    addi x2, x2, -2     # Should NOT execute
    
label1:
    addi x2, x2, 1      # x2 = 1 (success)

    # JALR
    la x3, label2     # Load address of label2 into x3
    jalr x4, 0(x3)   # Jump to address in x3, do not save return address
    addi x5, x5, -2    # Should NOT execute

label2:
    addi x5, x5, 1     # x5 = 1 (success)
    
    ebreak
