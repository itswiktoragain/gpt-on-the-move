SHELL := /bin/bash
CC := gcc
NASM := nasm
LD := ld
CFLAGS := -std=c11 -O2 -Wall -Wextra -Werror -ffreestanding -fno-stack-protector -fno-pie -fno-pic -m64 -mno-red-zone -mgeneral-regs-only -mcmodel=kernel -fno-builtin -fno-asynchronous-unwind-tables -fno-omit-frame-pointer
LDFLAGS := -nostdlib -static -m elf_x86_64 -z max-page-size=0x1000 -T src/link.ld

.PHONY: all clean test
all: build/w2k64-amd64.iso

build:
	mkdir -p build

build/boot.o: src/boot.asm | build
	$(NASM) -f elf64 $< -o $@

build/kernel.o: src/kernel.c src/font8x8.h src/dos16.h src/pe.h src/vmware_svga.h | build
	$(CC) $(CFLAGS) -c $< -o $@

build/dos16.o: src/dos16.c src/dos16.h | build
	$(CC) $(CFLAGS) -c $< -o $@

build/vmware_svga.o: src/vmware_svga.c src/vmware_svga.h | build
	$(CC) $(CFLAGS) -c $< -o $@

build/pe.o: src/pe.c src/pe.h | build
	$(CC) $(CFLAGS) -c $< -o $@

build/w2k64.elf: build/boot.o build/kernel.o build/dos16.o build/pe.o build/vmware_svga.o src/link.ld | build
	$(LD) $(LDFLAGS) -o $@ build/boot.o build/kernel.o build/dos16.o build/pe.o build/vmware_svga.o
	grub-file --is-x86-multiboot2 $@
	readelf -h $@ | grep 'ELF64'
	readelf -h $@ | grep 'Advanced Micro Devices X86-64'

build/hello.com: | build
	python3 tools/make_com.py $@

build/w2k64-amd64.iso: build/w2k64.elf build/hello.com grub.cfg | build
	mkdir -p build/iso/boot/grub
	cp build/w2k64.elf build/iso/boot/w2k64.elf
	cp build/hello.com build/iso/boot/hello.com
	cp grub.cfg build/iso/boot/grub/grub.cfg
	grub-mkrescue -o $@ build/iso

test: all
	bash tests/boot_smoke.sh

clean:
	rm -rf build
