OUTPUT_FORMAT(elf64-x86-64)
ENTRY(start32)
SECTIONS
{
    . = 0x00100000;
    .multiboot ALIGN(8) : { KEEP(*(.multiboot)) }
    .text ALIGN(4096) : { *(.text*) }
    .rodata ALIGN(4096) : { *(.rodata*) }
    .data ALIGN(4096) : { *(.data*) }
    .bss ALIGN(4096) (NOLOAD) : { *(COMMON) *(.bss*) }
    /DISCARD/ : { *(.eh_frame*) *(.comment*) *(.note*) }
}
