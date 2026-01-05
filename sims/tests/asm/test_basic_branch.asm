.section .text
.globl _start

_start:
    lui x10, 0x80000
    
    # BEQ (Branch if Equal)
    addi x1, x0, 10       # x1 = 10
    addi x2, x0, 10       # x2 = 10
    beq x1, x2, beq_taken # Should branch (equal)
    addi x3, x0, -1       # Should NOT execute
beq_taken:
    addi x3, x0, 1        # x3 = 1 (success)
beq_end_0:

    ebreak
