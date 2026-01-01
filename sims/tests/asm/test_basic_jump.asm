.section .text
.globl _start

_start:
    lui x10, 0x80000

    # Test 1: Simple JAL (jump forward)
    jal x1, label1      # Jump to label1, save PC+4 in x1
    addi x2, x0, 99     # Should NOT execute
    
label1:
    addi x3, x0, 1      # x3 = 1 (jump worked)
    
    # Test 2: JALR (jump using register)
    la x4, label2       # Load address of label2 into x4
    jalr x5, x4, 0      # Jump to x4, save PC+4 in x5
    addi x6, x0, 99     # Should NOT execute
    
label2:
    addi x7, x0, 2      # x7 = 2 (jalr worked)
    
    # Test 3: AUIPC
    auipc x8, 0x100     # x8 = PC + (0x100 << 12)
    
    # Test 4: JAL to zero register (just jump, no link)
    jal x0, label3      # Just jump, don't save return address
    addi x9, x0, 99     # Should NOT execute
    
label3:
    addi x11, x0, 3     # x11 = 3
    
    ebreak
