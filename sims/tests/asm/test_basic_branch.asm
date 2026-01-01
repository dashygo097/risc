.section .text
.globl _start

_start:
    lui x10, 0x80000
    
    # Test 1: BEQ (Branch if Equal)
    addi x1, x0, 10       # x1 = 10
    addi x2, x0, 10       # x2 = 10
    beq x1, x2, beq_taken # Should branch (equal)
    addi x3, x0, 99       # Should NOT execute
    j beq_end
beq_taken:
    addi x3, x0, 1        # x3 = 1 (branch was taken)
beq_end:

    # Test 2: BEQ (Branch not taken)
    addi x4, x0, 10       # x4 = 10
    addi x5, x0, 20       # x5 = 20
    beq x4, x5, beq_fail  # Should NOT branch (not equal)
    addi x6, x0, 2        # x6 = 2 (branch not taken)
    j beq_test2_end
beq_fail:
    addi x6, x0, 99       # Should NOT execute
beq_test2_end:

    # Test 3: BNE (Branch if Not Equal)
    addi x7, x0, 15       # x7 = 15
    addi x8, x0, 25       # x8 = 25
    bne x7, x8, bne_taken # Should branch (not equal)
    addi x9, x0, 99       # Should NOT execute
    j bne_end
bne_taken:
    addi x9, x0, 3        # x9 = 3 (branch was taken)
bne_end:

    # Test 4: BNE (Branch not taken)
    addi x11, x0, 30      # x11 = 30
    addi x12, x0, 30      # x12 = 30
    bne x11, x12, bne_fail # Should NOT branch (equal)
    addi x13, x0, 4       # x13 = 4 (branch not taken)
    j bne_test2_end
bne_fail:
    addi x13, x0, 99      # Should NOT execute
bne_test2_end:

    # Test 5: BLT (Branch if Less Than, signed)
    addi x14, x0, 5       # x14 = 5
    addi x15, x0, 10      # x15 = 10
    blt x14, x15, blt_taken # Should branch (5 < 10)
    addi x16, x0, 99      # Should NOT execute
    j blt_end
blt_taken:
    addi x16, x0, 5       # x16 = 5 (branch was taken)
blt_end:

    # Test 6: BLT with negative numbers
    addi x17, x0, -5      # x17 = -5
    addi x18, x0, 3       # x18 = 3
    blt x17, x18, blt_neg_taken # Should branch (-5 < 3)
    addi x19, x0, 99      # Should NOT execute
    j blt_neg_end
blt_neg_taken:
    addi x19, x0, 6       # x19 = 6 (branch was taken)
blt_neg_end:

    # Test 7: BGE (Branch if Greater or Equal, signed)
    addi x20, x0, 20      # x20 = 20
    addi x21, x0, 15      # x21 = 15
    bge x20, x21, bge_taken # Should branch (20 >= 15)
    addi x22, x0, 99      # Should NOT execute
    j bge_end
bge_taken:
    addi x22, x0, 7       # x22 = 7 (branch was taken)
bge_end:

    # Test 8: BGE with equal values
    addi x23, x0, 25      # x23 = 25
    addi x24, x0, 25      # x24 = 25
    bge x23, x24, bge_eq_taken # Should branch (25 >= 25)
    addi x25, x0, 99      # Should NOT execute
    j bge_eq_end
bge_eq_taken:
    addi x25, x0, 8       # x25 = 8 (branch was taken)
bge_eq_end:

    # Test 9: BLTU (Branch if Less Than, unsigned)
    addi x26, x0, 10      # x26 = 10
    addi x27, x0, 20      # x27 = 20
    bltu x26, x27, bltu_taken # Should branch (10 < 20 unsigned)
    addi x28, x0, 99      # Should NOT execute
    j bltu_end
bltu_taken:
    addi x28, x0, 9       # x28 = 9 (branch was taken)
bltu_end:

    # Test 10: BLTU with unsigned comparison
    addi x29, x0, -1      # x29 = 0xFFFFFFFF (large unsigned)
    addi x30, x0, 1       # x30 = 1
    bltu x30, x29, bltu_unsigned_taken # Should branch (1 < 0xFFFFFFFF unsigned)
    addi x31, x0, 99      # Should NOT execute
    j bltu_unsigned_end
bltu_unsigned_taken:
    addi x31, x0, 10      # x31 = 10 (branch was taken)
bltu_unsigned_end:

    # Test 11: BGEU (Branch if Greater or Equal, unsigned)
    addi x1, x0, 50       # x1 = 50
    addi x2, x0, 40       # x2 = 40
    bgeu x1, x2, bgeu_taken # Should branch (50 >= 40 unsigned)
    addi x3, x0, 99       # Should NOT execute
    j bgeu_end
bgeu_taken:
    addi x3, x0, 11       # x3 = 11 (branch was taken)
bgeu_end:

    # Test 12: Nested branches
    addi x4, x0, 5        # x4 = 5
    addi x5, x0, 5        # x5 = 5
    beq x4, x5, nested1   # Should branch
    addi x6, x0, 99       # Should NOT execute
    j nested_end
nested1:
    addi x6, x0, 15       # x6 = 15
    addi x7, x0, 10       # x7 = 10
    blt x7, x6, nested2   # Should branch (10 < 15)
    addi x8, x0, 99       # Should NOT execute
    j nested_end
nested2:
    addi x8, x0, 12       # x8 = 12 (both branches taken)
nested_end:

    # Test 13: Back-to-back branches (pipeline hazard test)
    addi x9, x0, 1        # x9 = 1
    addi x10, x0, 1       # x10 = 1
    beq x9, x10, bb1      # First branch
bb1:
    addi x11, x0, 2       # x11 = 2
    addi x12, x0, 2       # x12 = 2
    beq x11, x12, bb2     # Second branch immediately after
bb2:
    addi x13, x0, 13      # x13 = 13

    # End of tests
    ebreak
