OUTPUT_ARCH("riscv")
ENTRY(_start)

MEMORY
{
    IMEM (rx)  : ORIGIN = 0x80000000, LENGTH = 256K
    DMEM (rw)  : ORIGIN = 0x80040000, LENGTH = 256K
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
        *(.srodata)
        *(.srodata*)
        *(.rodata)
        *(.rodata*)
        . = ALIGN(4);
    } > IMEM

    .data : {
        . = ALIGN(4);
        __data_start = .;
        __sdata_start = .;
        PROVIDE(__global_pointer$ = __sdata_start + 0x800);

        *(.sdata)
        *(.sdata*)
        *(.data)
        *(.data*)

        . = ALIGN(4);
        __data_end = .;
    } > DMEM

    .bss : {
        . = ALIGN(4);
        _sbss = .;
        __bss_start = .;

        *(.sbss)
        *(.sbss*)
        *(.scommon)
        *(.bss)
        *(.bss*)
        *(COMMON)

        . = ALIGN(4);
        _ebss = .;
        __bss_end = .;
    } > DMEM

    . = ALIGN(16);
    __end = .;
    _end = .;

    __dmem_base = ORIGIN(DMEM);
    __stack = ORIGIN(DMEM) + LENGTH(DMEM);
    __stack_top = __stack;

    /DISCARD/ : {
        *(.comment)
        *(.eh_frame)
        *(.riscv.attributes)
        *(.note*)
    }
}
