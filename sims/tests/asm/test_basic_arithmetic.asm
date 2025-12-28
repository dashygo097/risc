.section .text
.globl _start

_start:
    lui x10, 0x80000
    
    addi x1, x0, 100      # x1 = 100
    addi x2, x0, 50       # x2 = 50
    
    add x3, x1, x2        # x3 = 150
    
    sub x4, x1, x2        # x4 = 50
    
    addi x5, x0, 0xFF     # x5 = 255
    addi x6, x0, 0x0F     # x6 = 15
    and x7, x5, x6        # x7 = 15
    
    or x8, x5, x6         # x8 = 255
    
    xor x9, x5, x6        # x9 = 240
    
    addi x11, x0, 1       # x11 = 1
    addi x12, x0, 4       # x12 = 4
    sll x13, x11, x12     # x13 = 16 (1 << 4)
    
    addi x14, x0, 32      # x14 = 32
    addi x15, x0, 2       # x15 = 2
    srl x16, x14, x15     # x16 = 8 (32 >> 2)
    
    addi x17, x0, -16     # x17 = -16 (0xFFFFFFF0)
    addi x18, x0, 2       # x18 = 2
    sra x19, x17, x18     # x19 = -4 (arithmetic shift preserves sign)
    
    addi x20, x0, 10      # x20 = 10
    addi x21, x0, 20      # x21 = 20
    slt x22, x20, x21     # x22 = 1 (10 < 20)
    slt x23, x21, x20     # x23 = 0 (20 < 10 is false)
    
    addi x24, x0, -1      # x24 = 0xFFFFFFFF
    addi x25, x0, 1       # x25 = 1
    sltu x26, x25, x24    # x26 = 1 (1 < 0xFFFFFFFF unsigned)

    ebreak
