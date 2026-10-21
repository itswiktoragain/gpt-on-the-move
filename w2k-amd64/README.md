# Windows 2000 AMD64 kernel port research - first executable milestone

**This project builds actual native x86-64 kernel instructions and a bootable ISO. It does not use Windows XP as its operating system.** It is nevertheless **not a completed Windows 2000 port**. The Windows 2000 Professional distribution contains closed-source x86-only kernel and OS executables; those cannot simply be relinked into AMD64.

## Actual native code in this project

- GRUB/Multiboot2 protected-mode entry, explicit transition to x86-64 long mode, and four-level paging, mapping first 4 GiB with huge pages.
- Freestanding AMD64 C kernel, serial COM1 diagnostics, PS/2 keyboard/mouse polling.
- Bootable 64-bit desktop research environment rendered through an RGB framebuffer or VGA text fallback. The About dialog contains the requested "(64-bit edition, a community made prototype)" wording.
- PE32/PE32+ header inspector that detects 32-bit vs AMD64 binaries.
- Working very small 16-bit DOS .COM instruction interpreter and INT 21h AH=02, 09, 4C handlers. It executes a bundled real DOS .COM sample during boot.

## Not yet implemented

- The actual Microsoft NT 5.0 x64 kernel, Windows 2000 installer, Explorer, driver model, subsystems, disk drivers, scheduler or user-mode Windows applications.
- CPU privilege separation, NT syscalls, a genuine WOW64 thunk layer, complete DOS/Windows NTVDM, or Win16 programs.
- The PE inspector does not execute Windows EXE programs. The 16-bit demonstration cannot run general Win16 or DOS applications.

This is the first native-kernel research milestone, not an OS replacement and not a renamed Windows XP ISO. It does not embed copyrighted Windows installation files.

## ISO download

A GitHub Actions workflow compiles and boots the ISO in QEMU, checks the AMD64 boot and DOS16 serial output, and only upon successful test publishes an ISO to the public pre-release:
https://github.com/itswiktoragain/gpt-on-the-move/releases/tag/w2k64-amd64-bootstrap

## Ubuntu build and test

    sudo apt update
    sudo apt install -y gcc binutils nasm make grub-pc-bin grub-common xorriso mtools qemu-system-x86 python3
    cd w2k-amd64
    make
    make test

For interactive graphical boot:

    qemu-system-x86_64 -M pc -m 512 -cdrom build/w2k64-amd64.iso -boot d -net none

F1: About; F2: run the included tiny DOS16 .COM; F3: PE/module info; F4: reboot; Enter: Start menu; Esc: close.

For your own Windows 2000 PE binary inspection, include a binary (from your personal copy) as another module2 entry in grub.cfg. No Win32 application execution is claimed, and licensed Microsoft binaries must not be redistributed in this public repository.

## Technical references
- GNU Multiboot2 specification: https://www.gnu.org/software/grub/manual/multiboot2/multiboot.html
- Microsoft WOW64: https://learn.microsoft.com/en-us/windows/win32/winprog64/wow64-implementation-details
- font8x8.h: Daniel Hepper and Marcel Sondaar public-domain VGA-derived font.
