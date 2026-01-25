OUTPUT_ARCH("riscv")
ENTRY(_start)

MEMORY
{
    IMEM (rx)  : ORIGIN = 0x00000000, LENGTH = 4K
    
    DMEM (rw)  : ORIGIN = 0x80000000, LENGTH = 16K
}

SECTIONS
{
    .text : {
        *(.text)
        *(.text*)
        . = ALIGN(4);
    } > IMEM

    .rodata : {
        *(.rodata)
        *(.rodata*)
        . = ALIGN(4);
    } > IMEM

    .data : {
        *(.data)
        *(.data*)
        . = ALIGN(4);
    } > DMEM

    .bss : {
        *(.bss)
        *(.bss*)
        *(COMMON)
        . = ALIGN(4);
    } > DMEM

    /DISCARD/ : {
        *(.comment)
        *(.eh_frame)
        *(.note*)
    }
}
