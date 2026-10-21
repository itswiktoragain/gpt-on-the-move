/* Windows 2000 AMD64 research kernel (native, freestanding AMD64).
 * NOT the proprietary NT 5.0 kernel; not a complete Windows NT implementation.
 * GRUB -> 32-bit bridge -> 64-bit paged long mode -> this entry point.
 */
#include <stddef.h>
#include <stdint.h>
#include "font8x8.h"
#include "dos16.h"
#include "pe.h"

#define RGB(r,g,b) (((uint32_t)(r)<<16)|((uint32_t)(g)<<8)|(uint32_t)(b))
#define COM1 0x3F8
#define TEAL RGB(0,119,120)
#define SILVER RGB(199,199,199)
#define NAVY RGB(1,27,115)
#define WHITE RGB(255,255,255)
#define BLACK RGB(0,0,0)
#define DARK RGB(43,43,43)
#define SHADOW RGB(118,118,118)

static inline void outb(uint16_t port,uint8_t value){
    __asm__ volatile("outb %0, %1"::"a"(value),"Nd"(port));
}
static inline uint8_t inb(uint16_t port){
    uint8_t value; __asm__ volatile("inb %1, %0":"=a"(value):"Nd"(port)); return value;
}
static inline void cpu_pause(void){__asm__ volatile("pause");}
static void serial_init(void){
    outb(COM1+1,0);outb(COM1+3,0x80);outb(COM1+0,1);outb(COM1+1,0);
    outb(COM1+3,3);outb(COM1+2,0xC7);outb(COM1+4,0x0B);
}
static void serial_char(char c){
    for(uint32_t t=0;t<100000u && !(inb(COM1+5)&0x20);++t)cpu_pause();
    outb(COM1,(uint8_t)c);
}
static void serial_str(const char *s){while(*s)serial_char(*s++);}
static void serial_u64(uint64_t v){
    char buf[24]; unsigned i=0;
    if(!v){serial_char('0');return;}
    while(v&&i<sizeof(buf)){buf[i++]=(char)('0'+(v%10));v/=10;}
    while(i)serial_char(buf[--i]);
}

typedef struct {
    uint8_t *base;
    uint32_t pitch,w,h;
    uint8_t bpp,red_pos,red_size,green_pos,green_size,blue_pos,blue_size;
    uint8_t ready;
} framebuffer_t;
static framebuffer_t fb;
static uint16_t vga_cursor;
static uint64_t ram_megabytes;
static uint32_t module_count;
static const uint8_t *dos_module;
static uint32_t dos_module_length;
static const uint8_t *pe_module;
static uint32_t pe_module_length;
static int screen_mode;  /* 0 desktop, 1 About, 2 compatibility, 3 modules */
static int start_menu;
static int mouse_x=128,mouse_y=128;
static int mouse_left,mouse_packet_index;
static uint8_t mouse_packet[3];
static char dos_output[128];
static unsigned dos_output_length;
static int dos_return=-99;

static void vga_print(const char *s){
    volatile uint16_t *vga=(volatile uint16_t *)(uintptr_t)0xB8000;
    while(*s){
        if(*s=='\n'){vga_cursor=(uint16_t)((vga_cursor/80+1)*80);s++;continue;}
        if(vga_cursor>=80*25)vga_cursor=0;
        vga[vga_cursor++]=(uint16_t)0x1F00|(uint8_t)*s++;
    }
}
static uint32_t mask8(uint8_t value,uint8_t size,uint8_t position){
    if(!size || size>8 || position>24)return 0;
    return (uint32_t)(value>>(8-size))<<position;
}
static uint32_t color_pack(uint32_t c){
    return mask8((uint8_t)(c>>16),fb.red_size,fb.red_pos)|
           mask8((uint8_t)(c>>8),fb.green_size,fb.green_pos)|
           mask8((uint8_t)c,fb.blue_size,fb.blue_pos);
}
static void put_pixel(int x,int y,uint32_t packed){
    if(!fb.ready||x<0||y<0||(unsigned)x>=fb.w||(unsigned)y>=fb.h)return;
    volatile uint8_t *p=fb.base+(size_t)y*fb.pitch+(size_t)x*(fb.bpp/8);
    p[0]=(uint8_t)packed;p[1]=(uint8_t)(packed>>8);p[2]=(uint8_t)(packed>>16);
    if(fb.bpp==32)p[3]=(uint8_t)(packed>>24);
}
static void rect(int x,int y,int w,int h,uint32_t color){
    if(!fb.ready ||w<=0||h<=0)return;
    if(x<0){w+=x;x=0;}if(y<0){h+=y;y=0;}
    if(x>=(int)fb.w||y>=(int)fb.h)return;
    if(w>(int)fb.w-x)w=(int)fb.w-x;
    if(h>(int)fb.h-y)h=(int)fb.h-y;
    uint32_t packed=color_pack(color);
    for(int j=0;j<h;++j)for(int i=0;i<w;++i)put_pixel(x+i,y+j,packed);
}
static void border(int x,int y,int w,int h,uint32_t light,uint32_t dark){
    rect(x,y,w,2,light);rect(x,y,2,h,light);
    rect(x+w-2,y,2,h,dark);rect(x,y+h-2,w,2,dark);
}
static void glyph(int x,int y,char c,uint32_t color,int scale){
    unsigned ch=(uint8_t)c;
    if(ch>127)ch='?';
    uint32_t packed=color_pack(color);
    for(int j=0;j<8;++j)for(int i=0;i<8;++i)
        if(font8x8_basic[ch][j]&(1u<<i))
            for(int ky=0;ky<scale;++ky)for(int kx=0;kx<scale;++kx)
                put_pixel(x+i*scale+kx,y+j*scale+ky,packed);
}
static void text(int x,int y,const char *s,uint32_t color,int scale){
    if(!fb.ready)return;
    for(;*s;++s){
        if(*s=='\n'){y+=11*scale;continue;}
        glyph(x,y,*s,color,scale);x+=8*scale;
        if(x>=(int)fb.w-8)break;
    }
}
static void label(int x,int y,int w,int h,const char *s){
    rect(x,y,w,h,SILVER);border(x,y,w,h,WHITE,SHADOW);
    text(x+10,y+(h-16)/2,s,BLACK,2);
}
static void draw_icon(int x,int y,const char *line1,const char *line2,int index){
    rect(x+14,y,33,32,RGB(8,39,143));border(x+14,y,33,32,WHITE,RGB(30,30,30));
    if(index==0){rect(x+18,y+5,25,3,WHITE);rect(x+18,y+12,25,3,WHITE);rect(x+18,y+19,20,3,WHITE);}
    else if(index==1){text(x+23,y+8,"16",WHITE,1);}
    else{text(x+27,y+9,"?",WHITE,2);}
    text(x,y+40,line1,WHITE,1);text(x,y+50,line2,WHITE,1);
}
static void draw_cursor(void){
    /* Draw a conventional one-piece arrow instead of a Microsoft four-square mark. */
    if(!fb.ready)return;
    for(int y=0;y<16;++y){
        for(int x=0;x<=y/2 && x<9;++x){
            if(x==0 || x==y/2 || y==15)put_pixel(mouse_x+x,mouse_y+y,color_pack(BLACK));
            else put_pixel(mouse_x+x,mouse_y+y,color_pack(WHITE));
        }
    }
}
static void draw_desktop(void){
    rect(0,0,(int)fb.w,(int)fb.h,TEAL);
    draw_icon(24,24,"My","Computer",0);
    draw_icon(24,114,"16-bit","Demo",1);
    draw_icon(24,204,"About","Windows",2);
    rect(0,(int)fb.h-42,(int)fb.w,42,SILVER);
    border(0,(int)fb.h-42,(int)fb.w,42,WHITE,SHADOW);
    label(4,(int)fb.h-36,118,31,"Start");
    rect((int)fb.w-193,(int)fb.h-36,185,30,RGB(214,214,214));
    border((int)fb.w-193,(int)fb.h-36,185,30,SHADOW,WHITE);
    text((int)fb.w-181,(int)fb.h-26,"AMD64  /  NT research",BLACK,1);
    text(18,(int)fb.h-66,"F1 About    F2 DOS16    F3 Modules    Enter Start",WHITE,1);
}
static void draw_window(int mode){
    int ww=(int)fb.w-140;if(ww>780)ww=780;
    int hh=mode==1?332:mode==2?310:350;
    int x=((int)fb.w-ww)/2,y=((int)fb.h-hh)/2-20;
    rect(x+7,y+7,ww,hh,RGB(14,50,50));
    rect(x,y,ww,hh,SILVER);border(x,y,ww,hh,WHITE,SHADOW);
    rect(x+4,y+4,ww-8,31,NAVY);
    text(x+14,y+12,mode==1?"About Windows":mode==2?"MS-DOS compatibility experiment":"Module inspector",WHITE,2);
    label(x+ww-37,y+7,27,25,"X");
    if(mode==1){
        rect(x+17,y+49,ww-34,82,WHITE);
        rect(x+18,y+49,8,82,NAVY);
        text(x+43,y+64,"Windows 2000 Professional",NAVY,2);
        text(x+43,y+96,"64-bit edition",NAVY,2);
        text(x+24,y+146,"(64-bit edition, a community made prototype)",DARK,1);
        text(x+24,y+170,"Native x86-64 kernel bootstrap: ACTIVE",DARK,1);
        text(x+24,y+190,"32-bit Windows WOW64: NOT IMPLEMENTED",DARK,1);
        text(x+24,y+210,"Win16/NTVDM: NOT IMPLEMENTED",DARK,1);
        text(x+24,y+230,"Experimental 16-bit DOS CPU subset: AVAILABLE",DARK,1);
        text(x+24,y+251,"Kernel: independent research code, NOT NT 5.0",DARK,1);
    }else if(mode==2){
        text(x+24,y+56,"16-bit DOS .COM interpreter smoke test",NAVY,2);
        text(x+24,y+99,"This runs a real tiny DOS .COM via interpreter.",DARK,1);
        text(x+24,y+121,"INT 21h: AH=02, 09, 4C; limited opcodes.",DARK,1);
        text(x+24,y+144,"Output:",DARK,2);
        text(x+24,y+170,dos_output,DARK,1);
        text(x+24,y+202,dos_return==0?"Exit code: 0 (success)":"Interpreter incomplete or program failed",DARK,1);
        text(x+24,y+232,"Full NTVDM / Win16 apps are not supported.",DARK,1);
    }else{
        text(x+24,y+56,"Executable image inspection (PE32 / PE32+)",NAVY,2);
        text(x+24,y+99,"Kernel runs as AMD64 (ring 0).",DARK,1);
        text(x+24,y+127,"GRUB modules are available for inspection.",DARK,1);
        text(x+24,y+154,"PE execution loader: NOT IMPLEMENTED",DARK,1);
        text(x+24,y+180,"WOW64 thunk layer: NOT IMPLEMENTED",DARK,1);
        text(x+24,y+208,"A DOS .COM module is included for CPU testing.",DARK,1);
        text(x+24,y+234,"See serial output for memory and module records.",DARK,1);
    }
    label(x+ww-112,y+hh-49,88,32,"OK");
}
static void redraw(void){
    if(!fb.ready)return;
    draw_desktop();
    if(screen_mode)draw_window(screen_mode);
    if(start_menu){
        int y=(int)fb.h-272;
        rect(4,y,260,229,SILVER);border(4,y,260,229,WHITE,SHADOW);
        rect(10,y+7,35,215,NAVY);
        text(54,y+19,"About Windows",BLACK,1);
        text(54,y+67,"Run DOS16 example",BLACK,1);
        text(54,y+115,"Inspect modules",BLACK,1);
        text(54,y+169,"Close menu [Esc]",BLACK,1);
    }
    draw_cursor();
}
static void output_from_dos(char c,void *opaque){
    (void)opaque;
    if(dos_output_length<sizeof(dos_output)-1)dos_output[dos_output_length++]=c;
    serial_char(c);
}
static void run_dos_demo(void){
    static const uint8_t hello[]={0xB4,0x09,0xBA,0x0C,0x01,0xCD,0x21,0xB8,0,0x4C,0xCD,0x21,
      'H','e','l','l','o',' ','f','r','o','m',' ','1','6','-','b','i','t',' ','D','O','S','!','$'};
    const uint8_t *source=dos_module?dos_module:hello;
    size_t length=dos_module?dos_module_length:sizeof(hello);
    dos_output_length=0;dos_output[0]=0;
    serial_str("DOS16: ");
    dos_return=dos16_execute_com(source,length,output_from_dos,NULL);
    dos_output[dos_output_length]=0;
    serial_str("\r\n");
}
static void init_mb2(uintptr_t mb2){
    if(!mb2)return;
    const uint8_t *m=(const uint8_t*)mb2;
    uint32_t total=*(const uint32_t*)m;
    serial_str("MB2 total bytes="); serial_u64(total); serial_str("\r\n");
    if(total<16 || total>1024*1024)return;
    const uint8_t *limit=m+total;
    unsigned tags_seen=0;
    for(const uint8_t *p=m+8;p+8<=limit && tags_seen++<128;){
        uint32_t type=*(const uint32_t*)p;
        uint32_t size=*(const uint32_t*)(p+4);
        serial_str("MB2 tag type=");serial_u64(type);
        serial_str(" length=");serial_u64(size);serial_str("\r\n");
        if(type==0)break;
        if(size<8 || size>(size_t)(limit-p))break;
        if(type==8 && size>=38){
            uint64_t physical=*(const uint64_t*)(p+8);
            uint32_t pitch=*(const uint32_t*)(p+16);
            uint32_t width=*(const uint32_t*)(p+20);
            uint32_t height=*(const uint32_t*)(p+24);
            uint8_t bits=p[28],format=p[29];
            if(format==1 && (bits==24||bits==32) && width>=640 && height>=480 &&
               width<=1920 && height<=1200 && pitch>=width*(bits/8) && 
               physical<0x100000000ULL &&
               (uint64_t)pitch*height<=0x100000000ULL-physical) {
                fb.base=(uint8_t*)(uintptr_t)physical;
                fb.pitch=pitch;fb.w=width;fb.h=height;fb.bpp=bits;
                fb.red_pos=p[32];fb.red_size=p[33];
                fb.green_pos=p[34];fb.green_size=p[35];
                fb.blue_pos=p[36];fb.blue_size=p[37];
                fb.ready=1;
            }
        }else if(type==3 && size>=17){
            ++module_count;
            uint32_t begin=*(const uint32_t*)(p+8),end=*(const uint32_t*)(p+12);
            if(end>=begin && (size_t)(end-begin)<16*1024*1024){
                const uint8_t *data=(const uint8_t*)(uintptr_t)begin;
                const char *name=(const char*)(p+16);
                serial_str("MODULE: ");serial_str(name);serial_str(" size=");
                serial_u64((uint64_t)end-begin);serial_str("\r\n");
                if(dos_module==NULL && name[0]=='D' && name[1]=='O' && name[2]=='S'){
                    dos_module=data;dos_module_length=end-begin;
                }
                pe_image_info image;
                if(pe_analyze(data,end-begin,&image)==0){
                    pe_module=data;pe_module_length=end-begin;
                    serial_str("PE: machine=");
                    if(image.machine==0x8664) serial_str("AMD64");
                    else if(image.machine==0x14C) serial_str("i386");
                    else serial_str("other");
                    serial_str("\r\n");
                }
            }
        }else if(type==6 && size>=16){
            uint32_t es=*(const uint32_t*)(p+8);
            if(es>=24){
                for(const uint8_t *e=p+16;e+es<=p+size;e+=es){
                    uint64_t count=*(const uint64_t*)(e+8);
                    uint32_t kind=*(const uint32_t*)(e+16);
                    if(kind==1)ram_megabytes+=count/1048576ULL;
                }
            }
        }
        p+=(size+7u)&~7u;
    }
    serial_str("MB2 scan done\r\n");
}
static void ps2_send_mouse(uint8_t value){
    for(uint32_t x=0;x<400000 && (inb(0x64)&2);++x)cpu_pause();
    outb(0x64,0xD4);
    for(uint32_t x=0;x<400000 && (inb(0x64)&2);++x)cpu_pause();
    outb(0x60,value);
}
static void init_mouse(void){
    /* PS/2 is optional. Do not rely on IRQs, which are still disabled. */
    for(uint32_t x=0;x<400000 && (inb(0x64)&2);++x)cpu_pause();
    outb(0x64,0xA8);
    ps2_send_mouse(0xF6);
    ps2_send_mouse(0xF4);
}
static void switch_mode(int mode){screen_mode=mode;start_menu=0;redraw();}
static void on_key(uint8_t scan){
    if(scan&0x80)return;
    if(scan==0x01){screen_mode=0;start_menu=0;redraw();}
    else if(scan==0x1C){start_menu=!start_menu;redraw();}
    else if(scan==0x3B){switch_mode(1);}
    else if(scan==0x3C){run_dos_demo();switch_mode(2);}
    else if(scan==0x3D){switch_mode(3);}
    else if(scan==0x3E){serial_str("Reboot requested\r\n");outb(0x64,0xFE);}
}
static void on_click(void){
    int y=(int)fb.h-42;
    if(mouse_y>=y && mouse_x<124){start_menu=!start_menu;redraw();return;}
    if(start_menu){
        int my=(int)fb.h-272;
        if(mouse_y<my+56 && mouse_y>my){switch_mode(1);return;}
        if(mouse_y>=my+56 && mouse_y<my+107){run_dos_demo();switch_mode(2);return;}
        if(mouse_y>=my+107 && mouse_y<my+162){switch_mode(3);return;}
        start_menu=0;redraw();return;
    }
    if(screen_mode){
        int ww=(int)fb.w-140;if(ww>780)ww=780;
        int hh=screen_mode==1?332:screen_mode==2?310:350;
        int x=((int)fb.w-ww)/2,wy=((int)fb.h-hh)/2-20;
        if((mouse_x>x+ww-45 && mouse_x<x+ww-5 && mouse_y>wy && mouse_y<wy+45) ||
           (mouse_x>x+ww-140 && mouse_x<x+ww-12 && mouse_y>wy+hh-56 && mouse_y<wy+hh-5)){
            switch_mode(0);return;
        }
    }else if(mouse_x<105){
        if(mouse_y<105){switch_mode(3);return;}
        if(mouse_y<195){run_dos_demo();switch_mode(2);return;}
        if(mouse_y<285){switch_mode(1);return;}
    }
}
static void on_mouse_byte(uint8_t byte){
    if(mouse_packet_index==0 && !(byte&0x08))return;
    mouse_packet[mouse_packet_index++]=byte;
    if(mouse_packet_index<3)return;
    mouse_packet_index=0;
    uint8_t flags=mouse_packet[0];
    if(flags&0xC0)return;
    mouse_x+=(int8_t)mouse_packet[1];mouse_y-=(int8_t)mouse_packet[2];
    if(mouse_x<0) mouse_x=0;
    if(mouse_y<0) mouse_y=0;
    if(mouse_x>=(int)fb.w)mouse_x=(int)fb.w-1;
    if(mouse_y>=(int)fb.h)mouse_y=(int)fb.h-1;
    int pressed=(flags&1)!=0;
    if(pressed && !mouse_left)on_click();
    mouse_left=pressed;
    redraw();
}
void kernel_main(uint32_t multiboot_information){
    serial_init();
    serial_str("\r\nW2K64: LONG_MODE=1 (native amd64)\r\n");
    serial_str("Kernel base: custom 64-bit research implementation; NOT Microsoft NT 5.0\r\n");
    init_mb2((uintptr_t)multiboot_information);
    serial_str("RAM MiB (available map): ");serial_u64(ram_megabytes);serial_str("\r\n");
    serial_str("Modules found: ");serial_u64(module_count);serial_str("\r\n");
    serial_str("WOW64: not implemented | NTVDM/Win16: not implemented\r\n");
    run_dos_demo();
    if(fb.ready){
        serial_str("GUI: framebuffer ready\r\n");
        mouse_x=(int)fb.w/2;mouse_y=(int)fb.h/2;
        redraw();
    }else{
        serial_str("GUI: framebuffer unavailable, text fallback\r\n");
        vga_print("Windows 2000 Professional (64-bit edition, a community made prototype)\n");
        vga_print("Native AMD64 boot proof, NOT a finished Windows 2000 NT 5.0 kernel\n");
        vga_print("F1 About; F2 DOS16; F3 Modules; F4 Reboot\n");
    }
    init_mouse();
    serial_str("W2K64: INPUT_LOOP=1\r\n");
    for(;;){
        uint8_t status=inb(0x64);
        if(status&1){
            uint8_t b=inb(0x60);
            if(status&0x20){if(fb.ready)on_mouse_byte(b);}
            else on_key(b);
        }else cpu_pause();
    }
}
