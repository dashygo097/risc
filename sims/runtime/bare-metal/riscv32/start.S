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

# ========================================================
# 默认的 C 语言中断处理函数 (弱符号)
# 如果别的 C 文件没有实现它，就进入死循环
# ========================================================
.weak timer_handler_c
timer_handler_c:
    j timer_handler_c

# ========================================================
# BSS 段：存放中断时的专用变量和栈内存
# ========================================================
.section .bss
.align 4
.globl saved_sp
saved_sp:
    .word 0          

.align 4
irq_stack_bottom:
    .space 1024      
irq_stack_top:       

# ========================================================
# 专用的中断入口汇编，安全换栈！
# ========================================================
.section .text
.globl trap_entry
.align 4
trap_entry:
    addi sp, sp, -128
    sw ra, 0(sp)
    sw t0, 4(sp)
    sw t1, 8(sp)
    sw t2, 12(sp)
    sw a0, 16(sp)
    sw a1, 20(sp)
    sw a2, 24(sp)
    sw a3, 28(sp)
    sw a4, 32(sp)
    sw a5, 36(sp)
    sw a6, 40(sp)
    sw a7, 44(sp)
    
    csrr t0, mepc
    sw t0, 48(sp)

    la t0, saved_sp
    sw sp, 0(t0)
    
    la sp, irq_stack_top

    call timer_handler_c

    la t0, saved_sp
    lw sp, 0(t0)

    lw t0, 48(sp)
    csrw mepc, t0

    lw ra, 0(sp)
    lw t0, 4(sp)
    lw t1, 8(sp)
    lw t2, 12(sp)
    lw a0, 16(sp)
    lw a1, 20(sp)
    lw a2, 24(sp)
    lw a3, 28(sp)
    lw a4, 32(sp)
    lw a5, 36(sp)
    lw a6, 40(sp)
    lw a7, 44(sp)
    addi sp, sp, 128

    mret
