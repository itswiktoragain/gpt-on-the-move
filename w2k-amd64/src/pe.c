#include "pe.h"
static uint16_t u16(const uint8_t *p){return (uint16_t)p[0]|((uint16_t)p[1]<<8);}
static uint32_t u32(const uint8_t *p){return (uint32_t)p[0]|((uint32_t)p[1]<<8)|((uint32_t)p[2]<<16)|((uint32_t)p[3]<<24);}
int pe_analyze(const void *input,size_t len,pe_image_info *out) {
    if (!input || !out || len < 0x40) return -1;
    const uint8_t *p=input;
    if (p[0]!='M'||p[1]!='Z') return -2;
    uint32_t offset=u32(p+0x3C);
    if (offset>len || len-offset<26) return -3;
    if (p[offset]!='P'||p[offset+1]!='E'||p[offset+2]||p[offset+3]) return -4;
    const uint8_t *coff=p+offset+4;
    uint16_t osize=u16(coff+16);
    if ((size_t)(len-offset-24)<osize || osize<20) return -5;
    out->machine=u16(coff);
    out->sections=u16(coff+2);
    out->format=u16(coff+20);
    out->entry_rva=u32(coff+20+16);
    return (out->format==0x10B || out->format==0x20B) ? 0 : -6;
}
