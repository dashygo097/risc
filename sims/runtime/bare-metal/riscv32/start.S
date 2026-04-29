# start.S - Minimal RISC-V startup code
.section .text.entry, "ax"
.globl _start

_start:
    .option push
    .option norelax
    la gp, __global_pointer$
    .option pop

    la sp, __stack
    andi sp, sp, -16

    mv fp, zero

    la t0, _sbss
    la t1, _ebss
    bgeu t0, t1, bss_clear_done

bss_clear_loop:
    sw zero, 0(t0)
    addi t0, t0, 4
    bltu t0, t1, bss_clear_loop

bss_clear_done:
    jal ra, main

halt:
    j halt
