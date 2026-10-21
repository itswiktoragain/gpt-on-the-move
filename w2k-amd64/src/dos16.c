#include "dos16.h"

/* 64 KiB emulated 8086 address space, sufficient for tiny COM demonstrations. */
static uint8_t memory[65536];
static uint16_t ax, bx, cx, dx, ip;
static uint8_t halted;
static uint16_t read_word(void) {
    uint8_t l = memory[ip++];
    uint8_t h = memory[ip++];
    return (uint16_t)l | ((uint16_t)h << 8);
}
int dos16_execute_com(const uint8_t *code, size_t code_size, dos16_output_fn output, void *opaque) {
    if (!code || !output || code_size == 0 || code_size > 0xFE00) return -1;
    for (size_t i=0; i<sizeof(memory); ++i) memory[i] = 0;
    for (size_t i=0; i<code_size; ++i) memory[0x100 + i] = code[i];
    ax=bx=cx=dx=0; ip=0x100; halted=0;
    uint32_t steps=0;
    while (!halted && steps++ < 100000u) {
        uint8_t op=memory[ip++];
        switch(op) {
            case 0x90: break; /* NOP */
            case 0xB8: ax=read_word(); break;
            case 0xB9: cx=read_word(); break;
            case 0xBA: dx=read_word(); break;
            case 0xBB: bx=read_word(); break;
            case 0xB4: ax=(uint16_t)((ax&0x00FF)|((uint16_t)memory[ip++]<<8)); break;
            case 0xB0: ax=(uint16_t)((ax&0xFF00)|memory[ip++]); break;
            case 0xEB: {int8_t disp=(int8_t)memory[ip++];ip=(uint16_t)(ip+disp);break;}
            case 0xF4: halted=1;break;
            case 0xCD: {
                uint8_t vector=memory[ip++];
                if(vector!=0x21) return -2;
                switch(ax>>8) {
                    case 0x09: {
                        uint16_t at=dx; unsigned limit=0;
                        while (limit++<65536u && memory[at]!='$') output((char)memory[at++],opaque);
                        if(limit>=65536u) return -4;
                        break;
                    }
                    case 0x02: output((char)(dx&0xFF),opaque);break;
                    case 0x4C: return (int)(ax&0xFF); /* DOS terminate */
                    default: return -3;
                }
                break;
            }
            default: return -5; /* unsupported opcode: no false claim of NTVDM */
        }
    }
    return halted ? 0 : -6;
}
