/*
 * glibc-bridge - Obstack Wrappers
 *
 * glibc obstack (object stack) - a memory allocation scheme.
 * Not available in Bionic, these are stub implementations.
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>

/* ============================================================================
 * Obstack error handler
 * ============================================================================ */

/* Global handler - can be set by user code
 * IMPORTANT: This must be exported as a DATA symbol, not a function.
 * box64 directly assigns to this variable: obstack_alloc_failed_handler = func;
 * So we export a pointer to this variable in the symbol table. */
void (*obstack_alloc_failed_handler)(void) = NULL;

/* Accessor for the global variable - returns the address of the variable */
void** get_obstack_alloc_failed_handler_ptr(void) {
    return (void**)&obstack_alloc_failed_handler;
}

/* ============================================================================
 * Obstack initialization and management
 * ============================================================================ */

int obstack_begin_wrapper(void* h, size_t size, size_t alignment,
                          void* (*chunkfun)(size_t), void (*freefun)(void*)) {
    (void)h;
    (void)size;
    (void)alignment;
    (void)chunkfun;
    (void)freefun;
    /* Stub - return success */
    return 1;
}

int obstack_begin_1_wrapper(void* h, size_t size, size_t alignment,
                            void* (*chunkfun)(void*, size_t), 
                            void (*freefun)(void*, void*),
                            void* arg) {
    (void)h;
    (void)size;
    (void)alignment;
    (void)chunkfun;
    (void)freefun;
    (void)arg;
    return 1;
}

void obstack_free_wrapper(void* h, void* obj) {
    (void)h;
    (void)obj;
    /* Stub */
}

/* ============================================================================
 * Obstack printf
 * ============================================================================ */

int obstack_vprintf_wrapper(void* obstack, const char* format, va_list ap) {
    (void)obstack;
    /* Fall back to vprintf for debugging purposes */
    return vprintf(format, ap);
}

int obstack_printf_wrapper(void* obstack, const char* format, ...) {
    (void)obstack;
    va_list ap;
    va_start(ap, format);
    int ret = vprintf(format, ap);
    va_end(ap);
    return ret;
}

int obstack_vprintf_chk_wrapper(void* obstack, int flag, const char* format, va_list ap) {
    (void)obstack;
    (void)flag;
    return vprintf(format, ap);
}

/* Direct obstack_free (not prefixed with underscore) */
void obstack_free_direct_wrapper(void* h, void* obj) {
    (void)h;
    (void)obj;
    /* Stub */
}

void obstack_newchunk_wrapper(void* h, size_t length) {
    (void)h;
    (void)length;
    /* Stub */
}

