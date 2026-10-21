#!/usr/bin/env python3
"""Create an actual 16-bit DOS .COM for the limited CPU interpreter smoke test."""
import pathlib
import sys

# mov ah,09h / mov dx,010Ch / int 21h / mov ax,4C00h / int 21h
program = bytes([0xB4, 0x09, 0xBA, 0x0C, 0x01, 0xCD, 0x21, 0xB8, 0, 0x4C, 0xCD, 0x21])
program += b"Hello from 16-bit DOS!$"
pathlib.Path(sys.argv[1]).write_bytes(program)
