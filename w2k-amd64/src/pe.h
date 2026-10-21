#ifndef PE_H
#define PE_H
#include <stddef.h>
#include <stdint.h>
typedef struct {
    uint16_t machine;
    uint16_t sections;
    uint16_t format;
    uint32_t entry_rva;
} pe_image_info;
int pe_analyze(const void *input, size_t length, pe_image_info *result);
#endif
