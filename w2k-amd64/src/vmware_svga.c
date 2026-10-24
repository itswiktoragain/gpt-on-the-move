/* VMware SVGA II bare-metal 2D driver for the AMD64 boot prototype.
 * Hardware protocol documented in the VMware SVGA Device Developer Kit:
 * https://github.com/prepare/vmware-svga
 *
 * This is not VMware Tools. It is a tiny mode-set and FIFO UPDATE implementation.
 * No Windows graphics driver, kernel mode driver API, 3D or cursor support.
 */
#include "vmware_svga.h"

extern uint64_t page_level4[512];

#define PAGE2M UINT64_C(0x200000)
#define APERTURE UINT64_C(0xFFFF800000000000)
#define SVGA_ID_2 0x90000002u
#define SVGA_ID_1 0x90000001u
#define SVGA_ID_0 0x90000000u
#define SVGA_REG_ID 0
#define SVGA_REG_ENABLE 1
#define SVGA_REG_WIDTH 2
#define SVGA_REG_HEIGHT 3
#define SVGA_REG_MAX_WIDTH 4
#define SVGA_REG_MAX_HEIGHT 5
#define SVGA_REG_BITS_PER_PIXEL 7
#define SVGA_REG_RED_MASK 9
#define SVGA_REG_GREEN_MASK 10
#define SVGA_REG_BLUE_MASK 11
#define SVGA_REG_BYTES_PER_LINE 12
#define SVGA_REG_FB_OFFSET 14
#define SVGA_REG_VRAM_SIZE 15
#define SVGA_REG_MEM_SIZE 19
#define SVGA_REG_CONFIG_DONE 20
#define SVGA_REG_SYNC 21
#define SVGA_REG_BUSY 22
#define SVGA_CMD_UPDATE 1

static uint64_t mmio_pdpt[512] __attribute__((aligned(4096)));
static uint64_t mmio_pd[2][512] __attribute__((aligned(4096)));
static volatile uint32_t *fifo;
static uint32_t fifo_size;
static uint16_t io_base;
static uint8_t svga_ready;

static inline void outl(uint16_t port,uint32_t val){
    __asm__ volatile("outl %0,%1"::"a"(val),"Nd"(port));
}
static inline uint32_t inl(uint16_t port){
    uint32_t v;__asm__ volatile("inl %1,%0":"=a"(v):"Nd"(port));return v;
}
void *video_map_physical(uint64_t address,uint64_t length,unsigned slot){
    if(slot>1 || !length || length>UINT64_C(0x40000000))return NULL;
    uint64_t aligned=address & ~(PAGE2M-1);
    uint64_t offset=address-aligned;
    if(length>UINT64_MAX-offset)return NULL;
    uint64_t count=(length+offset+PAGE2M-1)/PAGE2M;
    if(count>512)return NULL;
    for(uint32_t i=0;i<512;++i)mmio_pd[slot][i]=0;
    for(uint64_t i=0;i<count;++i){
        uint64_t physical=aligned+i*PAGE2M;
        mmio_pd[slot][i]=physical|0x83u; /* present, writable, 2MiB */
    }
    mmio_pdpt[slot]=(uint64_t)(uintptr_t)mmio_pd[slot]|3u;
    page_level4[256]=(uint64_t)(uintptr_t)mmio_pdpt|3u;
    __asm__ volatile("mov %%cr3, %%rax; mov %%rax, %%cr3":::"rax","memory");
    return (void*)(uintptr_t)(APERTURE+(uint64_t)slot*UINT64_C(0x40000000)+offset);
}
static uint32_t pci_read(uint8_t bus,uint8_t dev,uint8_t fn,uint8_t reg){
    outl(0xCF8,0x80000000u|((uint32_t)bus<<16)|((uint32_t)dev<<11)|((uint32_t)fn<<8)|(reg&0xFC));
    return inl(0xCFC);
}
static void pci_write(uint8_t bus,uint8_t dev,uint8_t fn,uint8_t reg,uint32_t value){
    outl(0xCF8,0x80000000u|((uint32_t)bus<<16)|((uint32_t)dev<<11)|((uint32_t)fn<<8)|(reg&0xFC));
    outl(0xCFC,value);
}
static uint64_t pci_bar(uint8_t b,uint8_t d,uint8_t f,unsigned idx){
    uint32_t raw=pci_read(b,d,f,(uint8_t)(0x10+idx*4u));
    if(raw==0 || raw==UINT32_MAX)return 0;
    if(raw&1u)return (uint64_t)(raw&~3u); /* I/O BAR */
    uint64_t physical=(uint64_t)(raw&~15u);
    if((raw&6u)==4u && idx<5)
        physical|=(uint64_t)pci_read(b,d,f,(uint8_t)(0x10+(idx+1)*4u))<<32;
    return physical;
}
static uint32_t read_reg(uint32_t index){
    outl(io_base,index);return inl((uint16_t)(io_base+1u));
}
static void write_reg(uint32_t index,uint32_t value){
    outl(io_base,index);outl((uint16_t)(io_base+1u),value);
}
static uint8_t bitshift(uint32_t mask){
    uint8_t count=0;if(mask==0)return 0;
    while(!(mask&1u)){mask>>=1;count++;}
    return count;
}
static uint8_t bitcount(uint32_t mask){
    uint8_t count=0;
    while(mask){count+=(uint8_t)(mask&1u);mask>>=1;}
    return count;
}
static void fifo_word(uint32_t value){
    if(!svga_ready)return;
    uint32_t begin=fifo[0],last=fifo[1],next=fifo[2],stop=fifo[3];
    if(begin<16||begin>=last||last>fifo_size ||(begin&3u) || (last&3u) || next<begin || next>=last)return;
    uint32_t future=next+4;
    if(future>=last)future=begin;
    if(future==stop){
        write_reg(SVGA_REG_SYNC,1);
        for(unsigned tries=0;tries<100000;++tries){
            if(read_reg(SVGA_REG_BUSY)==0)break;
            __asm__ volatile("pause");
        }
        stop=fifo[3];
        if(future==stop)return;
    }
    fifo[next/4]=value;
    __asm__ volatile("":::"memory");
    fifo[2]=future;
}
int vmware_svga_start(vmware_fb_info *out){
    if(!out)return 0;
    uint8_t bus=0,dev=0,fn=0,found=0;
    for(unsigned b=0;b<256&&!found;++b){
        for(unsigned d=0;d<32&&!found;++d){
            if(pci_read((uint8_t)b,(uint8_t)d,0,0)==UINT32_MAX)continue;
            for(unsigned f=0;f<8;++f){
                uint32_t id=pci_read((uint8_t)b,(uint8_t)d,(uint8_t)f,0);
                if((id&0xFFFFu)==0x15ADu && (id>>16)==0x0405u){
                    bus=(uint8_t)b;dev=(uint8_t)d;fn=(uint8_t)f;found=1;break;
                }
            }
        }
    }
    if(!found)return 0;
    uint32_t io_raw=pci_read(bus,dev,fn,0x10);
    if((io_raw&1u)==0)return 0;
    io_base=(uint16_t)(io_raw&~3u);
    if(io_base<0x100u || io_base>0xFFF0u)return 0;
    uint64_t fb_physical=pci_bar(bus,dev,fn,1);
    uint64_t fifo_physical=pci_bar(bus,dev,fn,2);
    if(!fb_physical || !fifo_physical)return 0;

    uint32_t command=pci_read(bus,dev,fn,0x04);
    pci_write(bus,dev,fn,0x04,command|3u); /* enable PCI I/O + MMIO */

    uint32_t version=SVGA_ID_2;
    while(version>=SVGA_ID_0){
        write_reg(SVGA_REG_ID,version);
        if(read_reg(SVGA_REG_ID)==version)break;
        if(version==SVGA_ID_0)return 0;
        --version;
    }
    uint32_t max_width=read_reg(SVGA_REG_MAX_WIDTH);
    uint32_t max_height=read_reg(SVGA_REG_MAX_HEIGHT);
    if(max_width<640 || max_height<480)return 0;
    uint32_t width=max_width>=1024?1024:800;
    if(width>max_width)width=640;
    uint32_t height=max_height>=768 && width==1024?768:600;
    if(height>max_height)height=480;

    write_reg(SVGA_REG_ENABLE,0);
    write_reg(SVGA_REG_WIDTH,width);
    write_reg(SVGA_REG_HEIGHT,height);
    write_reg(SVGA_REG_BITS_PER_PIXEL,32);
    write_reg(SVGA_REG_ENABLE,1);

    width=read_reg(SVGA_REG_WIDTH);
    height=read_reg(SVGA_REG_HEIGHT);
    uint32_t bpp=read_reg(SVGA_REG_BITS_PER_PIXEL);
    uint32_t pitch=read_reg(SVGA_REG_BYTES_PER_LINE);
    uint32_t offset=read_reg(SVGA_REG_FB_OFFSET);
    uint32_t vram=read_reg(SVGA_REG_VRAM_SIZE);
    uint32_t size=read_reg(SVGA_REG_MEM_SIZE);
    if(width<640||width>1920||height<480||height>1200)return 0;
    if(bpp!=16 && bpp!=24 && bpp!=32)return 0;
    if(pitch<width*(bpp/8)||pitch>64*1024)return 0;
    if((uint64_t)offset+(uint64_t)pitch*height>vram)return 0;
    if(size<4096||size>64*1024*1024)return 0;

    uint8_t *fb=(uint8_t*)video_map_physical(fb_physical+offset,(uint64_t)pitch*height,0);
    fifo=(volatile uint32_t*)video_map_physical(fifo_physical,size,1);
    if(!fb || !fifo)return 0;
    fifo_size=size;
    fifo[0]=16; fifo[1]=size; fifo[2]=16; fifo[3]=16;
    __asm__ volatile("":::"memory");
    write_reg(SVGA_REG_CONFIG_DONE,1);
    out->base=fb;
    out->width=width;out->height=height;out->pitch=pitch;out->bpp=(uint8_t)bpp;
    uint32_t red=read_reg(SVGA_REG_RED_MASK);
    uint32_t green=read_reg(SVGA_REG_GREEN_MASK);
    uint32_t blue=read_reg(SVGA_REG_BLUE_MASK);
    if(!red||!green||!blue){
        red=bpp==16?0xF800u:0x00FF0000u;
        green=bpp==16?0x07E0u:0x0000FF00u;
        blue=bpp==16?0x001Fu:0x000000FFu;
    }
    out->red_pos=bitshift(red);out->red_size=bitcount(red);
    out->green_pos=bitshift(green);out->green_size=bitcount(green);
    out->blue_pos=bitshift(blue);out->blue_size=bitcount(blue);
    svga_ready=1;
    return 1;
}
void vmware_svga_present(uint32_t width,uint32_t height){
    if(!svga_ready)return;
    /* The framebuffer is not presented until UPDATE arrives on VMware SVGA. */
    fifo_word(SVGA_CMD_UPDATE);
    fifo_word(0);fifo_word(0);
    fifo_word(width);fifo_word(height);
    write_reg(SVGA_REG_SYNC,1);
}
