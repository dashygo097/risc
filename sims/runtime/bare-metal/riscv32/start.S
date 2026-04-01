# start.S - Minimal RISC-V startup code
.section .text.entry, "ax"
.global _start
.extern main
.extern _sbss
.extern _ebss

_start:
    csrw mstatus, zero
    csrw mie, zero

    lui sp, 0x80040
    mv fp, zero

    la t0, _sbss
    la t1, _ebss
    beq t0, t1, 2f
1:  sb zero, 0(t0)
    addi t0, t0, 1
    bltu t0, t1, 1b
2:
    jal ra, main

halt:
    j halt
