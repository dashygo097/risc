# start.S - Minimal RISC-V startup code
.section .text
.global _start       
.extern main      

_start:
    lui sp, 0x80004
    mv fp, zero
    jal ra, main

    ebreak
    j .
