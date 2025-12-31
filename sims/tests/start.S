# start.S - Minimal RISC-V startup code
.section .text
.global _start       
.extern main      

_start:
    li sp, 0x80001000
    mv fp, zero
    jal ra, main
    ebreak
