#ifndef DOS16_H
#define DOS16_H
#include <stddef.h>
#include <stdint.h>
typedef void (*dos16_output_fn)(char character, void *opaque);
/* Small 16-bit DOS .COM subset interpreter. NOT Windows NTVDM and NOT Win16. */
int dos16_execute_com(const uint8_t *code, size_t code_size, dos16_output_fn output, void *opaque);
#endif
