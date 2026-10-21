; Native AMD64 bootstrap. Multiboot2 enters in 32-bit protected mode.
; Set up 4 GiB of identity-mapped 2 MiB pages and jump to long mode.
BITS 32

SECTION .multiboot ALIGN=8
header_start:
    dd 0xE85250D6
    dd 0
    dd header_end - header_start
    dd -(0xE85250D6 + (header_end - header_start))
    ; Prefer an RGB framebuffer. The kernel also handles EGA text mode.
    dw 5, 1
    dd 20
    dd 1024, 768, 32
    dd 0
    dw 0, 0
    dd 8
header_end:

SECTION .text
GLOBAL start32
EXTERN kernel_main

start32:
    cli
    mov esp, bootstrap_stack_end
    mov [mb2_address], ebx
    cmp eax, 0x36D76289
    jne bad_cpu

    ; CPUID long-mode support.
    mov eax, 0x80000000
    cpuid
    cmp eax, 0x80000001
    jb bad_cpu
    mov eax, 0x80000001
    cpuid
    test edx, (1 << 29)
    jz bad_cpu

    ; Four page directories, 512 x 2 MiB per directory = first 4 GiB.
    xor ecx, ecx
.fill_page_tables:
    mov eax, ecx
    shl eax, 21
    or eax, 0x83
    mov [page_directories + ecx * 8], eax
    mov dword [page_directories + ecx * 8 + 4], 0
    inc ecx
    cmp ecx, 2048
    jne .fill_page_tables

    mov eax, page_directories + 0x0000
    or eax, 3
    mov [page_directory_pointer + 0], eax
    mov eax, page_directories + 0x1000
    or eax, 3
    mov [page_directory_pointer + 8], eax
    mov eax, page_directories + 0x2000
    or eax, 3
    mov [page_directory_pointer + 16], eax
    mov eax, page_directories + 0x3000
    or eax, 3
    mov [page_directory_pointer + 24], eax

    mov eax, page_directory_pointer
    or eax, 3
    mov [page_level4], eax
    mov eax, page_level4
    mov cr3, eax
    mov eax, cr4
    or eax, (1 << 5)         ; PAE
    mov cr4, eax
    mov ecx, 0xC0000080
    rdmsr
    or eax, (1 << 8)         ; EFER.LME
    wrmsr
    mov eax, cr0
    or eax, (1 << 31)        ; paging
    mov cr0, eax
    lgdt [gdt_descriptor]
    jmp 0x08:start64

bad_cpu:
    mov dword [0xB8000], 0x4F524F45
.halt:
    cli
    hlt
    jmp .halt

BITS 64
start64:
    mov ax, 0x10
    mov ds, ax
    mov es, ax
    mov ss, ax
    xor ax, ax
    mov fs, ax
    mov gs, ax
    lea rsp, [rel bootstrap_stack_end]
    and rsp, -16
    mov edi, [rel mb2_address]
    call kernel_main
.done:
    cli
    hlt
    jmp .done

SECTION .rodata ALIGN=16
gdt64:
    dq 0
    dq 0x00AF9A000000FFFF
    dq 0x00CF92000000FFFF
gdt64_end:
gdt_descriptor:
    dw gdt64_end - gdt64 - 1
    dd gdt64

SECTION .bss
ALIGNB 4096
page_level4: resb 4096
page_directory_pointer: resb 4096
page_directories: resb 16384
ALIGNB 16
mb2_address: resd 1
ALIGNB 16
bootstrap_stack: resb 65536
bootstrap_stack_end:
