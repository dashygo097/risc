# start.S - Minimal RISC-V startup code
.global _start       
.extern main      

_start:
    csrw mstatus, zero
    csrw mie, zero

    lui sp, 0x80004
    mv fp, zero

    jal ra, main

    j .

