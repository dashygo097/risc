.globl _start
_start:
    lui x10, 0x80000

    addi x3, x0, 0        # Initialize x3 = 0
    addi x6, x0, 0        # Initialize x6 = 0
    addi x9, x0, 0        # Initialize x9 = 0
    addi x13, x0, 0       # Initialize x13 = 0
    addi x16, x0, 0       # Initialize x16 = 0
    
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
    jal x0, test3           # Skip failure marker
beq_fail:
    addi x6, x6, -2       # FAIL

test3:
    # BGE (Branch if Greater or Equal) - Should Branch
    addi x7, x0, 50       # x7 = 50
    addi x8, x0, 40       # x8 = 40
    bge x7, x8, bge_taken # Should branch (50 >= 40)
    addi x9, x9, -2        # FAIL: Should NOT execute
    jal x0, test4         # Skip success marker

bge_taken:
    addi x9, x9, 1        # SUCCESS: x9 = 1

test4:
    # BGE (Branch if Greater or Equal) - Should NOT Branch
    addi x11, x0, 15      # x11 = 15
    addi x12, x0, 25      # x12 = 25
    bge x11, x12, bge_fail # Should NOT branch (15 < 25)
    addi x13, x13, 1      # SUCCESS: x13 = 1 (branch not taken)
    jal x0, test5           # Skip failure marker

bge_fail:
    addi x13, x13, -2     # FAIL

end: 
    addi x16, x16, 1      # SUCCESS: x16 = 1
    ebreak

test5:
    # BGE (Branch if Greater or Equal) - Should Branch (jump backwards)
    addi x14, x0, 100     # x14 = 100
    addi x15, x0, 90      # x15 = 90
    bge x14, x15, end # Should branch (100 >= 90)
    addi x16, x16, -1     # FAIL: Should NOT execute
