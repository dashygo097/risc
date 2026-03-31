# start.S - Minimal RISC-V startup code
.section .text.entry
.globl _start

_start:
    csrw mstatus, zero
    csrw mie, zero

    li sp, 0x80004000

    call main

halt:
    j halt
