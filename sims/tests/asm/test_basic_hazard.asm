.section .text
.globl _start

_start:
    lui x10, 0x80000

    # Store-Load Hazard
    addi x1, x0, 10
    sw x1, 0(x10)
    lw x2, 0(x10)    # Hazard: load immediately after store
    
    # Load-Use Hazard
    lw x3, 0(x10)    # Hazard: load followed by use
    addi x4, x3, 5   # Use of loaded value

    # Load-Branch Hazard (Taken)
    lw x5, 0(x10)    # Hazard: load followed by branch
    addi x6, x0, 10
    beq x5, x6, branch_taken 
    addi x7, x0, -2 # failure

branch_taken:
    addi x7, x0, 1 # success

    # Load-Branch Hazard (Not Taken)
    lw x8, 0(x10)    # Hazard: load followed by branch
    addi x9, x0, 20
    beq x8, x9, branch_not_taken
    addi x7, x0, 1 # success 

branch_not_taken:
    addi x7, x0, -2 # failure

    ebreak
