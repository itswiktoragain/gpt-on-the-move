#include "../src/dos16.h"
#include "../src/pe.h"
#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <assert.h>

static char output[96]; static unsigned indexp=0;
static void emit(char c,void *context){(void)context;if(indexp<sizeof(output)-1)output[indexp++]=c;}
int main(void){
    const uint8_t dos[]={0xB4,9,0xBA,0x0C,1,0xCD,0x21,0xB8,0,0x4C,0xCD,0x21,
        'H','e','l','l','o',' ','f','r','o','m',' ','1','6','-','b','i','t',' ','D','O','S','!','$'};
    assert(dos16_execute_com(dos,sizeof(dos),emit,NULL)==0);
    output[indexp]=0;
    assert(strcmp(output,"Hello from 16-bit DOS!")==0);
    uint8_t pe[512]={0};pe[0]='M';pe[1]='Z';pe[0x3C]=0x80;
    pe[0x80]='P';pe[0x81]='E';
    pe[0x84]=0x64;pe[0x85]=0x86;
    pe[0x86]=3;pe[0x94]=0xF0;
    pe[0x98]=0x0B;pe[0x99]=0x02;
    pe[0xA8]=0x34;pe[0xA9]=0x12;
    pe_image_info info;
    assert(pe_analyze(pe,sizeof(pe),&info)==0);
    assert(info.machine==0x8664 && info.sections==3 && info.format==0x20B && info.entry_rva==0x1234);
    assert(pe_analyze(dos,sizeof(dos),&info)<0);
    puts("PASS: 16-bit DOS COM interpreter and AMD64 PE32+ parser");
    return 0;
}
