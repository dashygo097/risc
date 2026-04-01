OUTPUT_ARCH("riscv")
ENTRY(_start)

MEMORY
{
    IMEM (rx)  : ORIGIN = 0x00000000, LENGTH = 256K
    DMEM (rw)  : ORIGIN = 0x80000000, LENGTH = 256K
}

SECTIONS
{
    .text : {
        KEEP(*(.text.entry))
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
        _sbss = .;
        *(.bss)
        *(.bss*)
        *(COMMON)
        . = ALIGN(4);
        _ebss = .;
    } > DMEM

    __stack = ORIGIN(DMEM) + LENGTH(DMEM);

    /DISCARD/ : {
        *(.comment)
        *(.eh_frame)
        *(.note*)
    }
}
