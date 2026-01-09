.section .text
.globl _start

_start:
    lui x10, 0x80000
    
    # BEQ (Branch if Equal)
    addi x1, x0, 10       # x1 = 10
    addi x2, x0, 10       # x2 = 10
    beq x1, x2, beq_taken # Should branch (equal)
    addi x3, x0, -2       # Should NOT execute
beq_taken:
    addi x3, x0, 1        # x3 = 1 (success)

    # BEQ (Branch if Equal) - Not Taken
    addi x4, x0, 20       # x4 = 20
    addi x5, x0, 30       # x5 = 30
    beq x4, x5, beq_not_taken # Should NOT branch (not equal)
    addi x6, x0, 1        # x6 = 1 (success)

    ebreak

beq_not_taken:
    addi x6, x0, -2       # Should NOT execute
