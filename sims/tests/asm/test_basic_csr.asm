.globl _start

_start:
    lui x10, 0x80000

    addi x1, x0, 0x12
    csrw 0x300, x1      # Write to mstatus
    csrr x2, 0x300      # Read from mstatus
    csrwi 0x300, 0x01   # Write immediate to mstatus
    csrr x3, 0x300      # Read from mstatus

    ebreak
