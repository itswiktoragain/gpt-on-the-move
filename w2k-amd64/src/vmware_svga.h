#ifndef VMWARE_SVGA_H
#define VMWARE_SVGA_H
#include <stdint.h>
#include <stddef.h>

typedef struct {
    uint8_t *base;
    uint32_t pitch,width,height;
    uint8_t bpp,red_pos,red_size,green_pos,green_size,blue_pos,blue_size;
} vmware_fb_info;

/* Maps physical MMIO into the kernel's higher canonical address space.
 * slot 0: graphical framebuffer, slot 1: FIFO. Max 1 GiB per slot.
 * A pointer returned here must NOT be dereferenced by firmware or user mode.
 */
void *video_map_physical(uint64_t address, uint64_t length, unsigned slot);
int vmware_svga_start(vmware_fb_info *out);
void vmware_svga_present(uint32_t width,uint32_t height);
#endif
