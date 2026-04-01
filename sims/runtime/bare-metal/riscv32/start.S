# start.S - Minimal RISC-V startup code
.section .text.entry, "ax"
.global _start

_start:
    csrw mstatus, zero
    csrw mie, zero

    la sp, __stack 
    mv fp, zero

    la t0, _sbss
    la t1, _ebss

    beq t0, t1, bss_clear_done

bss_clear_loop: 
    sb zero, 0(t0)
    addi t0, t0, 1
    bltu t0, t1, bss_clear_done 

bss_clear_done:
    jal ra, main

halt:
    j halt
