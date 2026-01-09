.globl _start
_start:
    lui x10, 0x80000

    addi x3, x0, 0        # Initialize x3 = 0
    addi x6, x0, 0        # Initialize x6 = 0
    
    # BEQ (Branch if Equal) - Should Branch
    addi x1, x0, 10       # x1 = 10
    addi x2, x0, 10       # x2 = 10
    beq x1, x2, beq_taken # Should branch (10 == 10)
    addi x3, x3, -2        # FAIL: Should NOT execute 
    jal x0, test2         # Skip success marker
beq_taken:
    addi x3, x3, 1        # SUCCESS: x3 = 1

test2:
    # BEQ (Branch if Equal) - Should NOT Branch
    addi x4, x0, 20       # x4 = 20
    addi x5, x0, 30       # x5 = 30
    beq x4, x5, beq_fail  # Should NOT branch (20 != 30)
    addi x6, x6, 1        # SUCCESS: x6 = 1 (branch not taken)
    jal x0, end           # Skip failure marker
beq_fail:
    addi x6, x6, -2       # FAIL

end:
    ebreak
