/*
 * glibc-bridge TLS Compatibility Layer
 * 
 * Provides compatibility between glibc TLS and Android bionic TLS.
 * 
 * Problem: Both glibc and bionic use tpidr_el0 for TLS, but with different layouts.
 * 
 * Bionic TLS layout (ARM64):
 *   tpidr_el0 points to tls_slot(0)
 *   - TLS_SLOT_DTV = 0              // DTV pointer  
 *   - TLS_SLOT_THREAD_ID = 1        // pthread_internal_t*
 *   - TLS_SLOT_OPENGL = 2
 *   - TLS_SLOT_OPENGL_API = 5
 *   - TLS_SLOT_STACK_GUARD = 6      // Stack canary
 *   - TLS_SLOT_SANITIZER = 7
 *   - TLS_SLOT_BIONIC_TLS = -1      // Offset -8 bytes
 *
 * glibc TLS layout (ARM64):
 *   tpidr_el0 points to TCB (Thread Control Block)
 *   - tcb[0] = DTV pointer
 *   - tcb[1] = private data
 *   - Before TCB: struct pthread (~2KB)
 *   - After TCB: DTV array
 *
 * Solution: Keep bionic TLS intact, provide glibc-compatible wrappers for
 * functions that access TLS (like __ctype_b_loc, errno, etc.)
 */

#ifndef GLIBC_BRIDGE_TLS_H
#define GLIBC_BRIDGE_TLS_H

#include <stdint.h>
#include <stddef.h>

/* ============================================================================
 * Bionic TLS Slot Definitions (from platform_bionic)
 * ============================================================================ */

/* ARM64 bionic TLS slots */
#define BIONIC_MIN_TLS_SLOT             (-2)
#define BIONIC_TLS_SLOT_NATIVE_BRIDGE   (-2)
#define BIONIC_TLS_SLOT_BIONIC_TLS      (-1)
#define BIONIC_TLS_SLOT_DTV             0
#define BIONIC_TLS_SLOT_THREAD_ID       1
#define BIONIC_TLS_SLOT_OPENGL          2
#define BIONIC_TLS_SLOT_OPENGL_API      5
#define BIONIC_TLS_SLOT_STACK_GUARD     6
#define BIONIC_TLS_SLOT_SANITIZER       7
#define BIONIC_MAX_TLS_SLOT             7
#define BIONIC_TLS_SLOTS                (BIONIC_MAX_TLS_SLOT - BIONIC_MIN_TLS_SLOT + 1)

/* ============================================================================
 * Bionic TLS Access (read current bionic TLS)
 * ============================================================================ */

/* Get the current bionic TLS pointer (reads tpidr_el0) */
static inline void** bionic_get_tls(void) {
    void** result;
    __asm__ volatile("mrs %0, tpidr_el0" : "=r"(result));
    return result;
}

/* Set tpidr_el0 (WARNING: this affects both bionic and glibc code!) */
static inline void bionic_set_tls(void* tls) {
    __asm__ volatile("msr tpidr_el0, %0" : : "r"(tls));
}

/* Get a specific TLS slot value */
static inline void* bionic_get_tls_slot(int slot) {
    void** tls = bionic_get_tls();
    return tls[slot - BIONIC_MIN_TLS_SLOT];
}

/* ============================================================================
 * glibc TLS Emulation
 * 
 * Instead of setting up real glibc TLS (which would break bionic),
 * we provide thread-local storage for glibc-specific data.
 * ============================================================================ */

/* glibc ctype table flags (matching glibc's implementation) */
#define _GLIBC_ISbit(bit)  ((bit) < 8 ? ((1 << (bit)) << 8) : ((1 << (bit)) >> 8))

#define _GLIBC_ISupper    _GLIBC_ISbit(0)   /* UPPERCASE */
#define _GLIBC_ISlower    _GLIBC_ISbit(1)   /* lowercase */
#define _GLIBC_ISalpha    _GLIBC_ISbit(2)   /* Alphabetic */
#define _GLIBC_ISdigit    _GLIBC_ISbit(3)   /* Numeric */
#define _GLIBC_ISxdigit   _GLIBC_ISbit(4)   /* Hexadecimal */
#define _GLIBC_ISspace    _GLIBC_ISbit(5)   /* Whitespace */
#define _GLIBC_ISprint    _GLIBC_ISbit(6)   /* Printing */
#define _GLIBC_ISgraph    _GLIBC_ISbit(7)   /* Graphical */
#define _GLIBC_ISblank    _GLIBC_ISbit(8)   /* Blank */
#define _GLIBC_IScntrl    _GLIBC_ISbit(9)   /* Control */
#define _GLIBC_ISpunct    _GLIBC_ISbit(10)  /* Punctuation */
#define _GLIBC_ISalnum    _GLIBC_ISbit(11)  /* Alphanumeric */

/* glibc-compatible TLS data structure
 * IMPORTANT: stack_guard MUST be at offset 0x28 for glibc compatibility!
 * glibc code accesses stack canary via FS:0x28 (or TPIDR_EL0:0x28 on ARM64)
 */
typedef struct glibc_compat_tls {
    /* Padding to align stack_guard at offset 0x28 */
    uint64_t _reserved0;                /* 0x00 */
    uint64_t _reserved1;                /* 0x08 */
    uint64_t _reserved2;                /* 0x10 */
    uint64_t _reserved3;                /* 0x18 */
    uint64_t _reserved4;                /* 0x20 */
    
    /* Stack canary - MUST be at offset 0x28 for glibc compatibility */
    uintptr_t stack_guard;              /* 0x28 */
    
    /* Other fields after the critical offset */
    const unsigned short* ctype_b;      /* Character classification table */
    const int* ctype_tolower;           /* To-lowercase table */
    const int* ctype_toupper;           /* To-uppercase table */
    int glibc_errno;                    /* errno for glibc programs */
    char* progname;                     /* Program invocation name */
    char* progname_full;                /* Full program path */
    
} glibc_compat_tls_t;

/* Compile-time verification that stack_guard is at offset 0x28 */
#ifndef __cplusplus
_Static_assert(offsetof(glibc_compat_tls_t, stack_guard) == 0x28,
               "stack_guard must be at offset 0x28 for glibc compatibility");
#endif

/* Global glibc-compatible TLS (thread-local in wrapper code) */
extern __thread glibc_compat_tls_t g_glibc_tls;

/* ============================================================================
 * Initialization Functions
 * ============================================================================ */

/* Initialize the glibc compatibility TLS layer */
void glibc_bridge_init_glibc_tls(void);

/* Copy stack guard from bionic TLS to glibc-compat TLS */
void glibc_bridge_sync_stack_guard(void);

/* Get errno location (for glibc __errno_location wrapper) */
int* glibc_bridge_errno_location(void);

/* Sync errno from bionic to glibc (call after bionic function returns) */
void glibc_bridge_sync_errno_from_bionic(void);

/* Sync errno silently (no error logging) */
void glibc_bridge_sync_errno_silent(void);

/* Macro to sync errno after calling bionic functions (logs errors) */
#define SYNC_ERRNO() glibc_bridge_sync_errno_from_bionic()

/* Macro to sync errno only (no logging, for successful calls) */
#define SYNC_ERRNO_SILENT() glibc_bridge_sync_errno_silent()

/* Smart sync - only logs if call failed (ret < 0 for int, NULL for ptr) */
#define SYNC_ERRNO_IF_FAIL(ret) do { \
    if ((long)(ret) < 0) { glibc_bridge_sync_errno_from_bionic(); } \
    else { glibc_bridge_sync_errno_silent(); } \
} while(0)

/* ============================================================================
 * ctype Wrappers (glibc uses different table format than bionic)
 * ============================================================================ */

/* These functions return pointers to glibc-format ctype tables */
const unsigned short** glibc_bridge_ctype_b_loc(void);
const int** glibc_bridge_ctype_tolower_loc(void);
const int** glibc_bridge_ctype_toupper_loc(void);

/* ============================================================================
 * Dynamic Library TLS Support
 * 
 * For dynamically loaded glibc libraries (like libcoreclr.so), we need to
 * provide TLS storage that works with TLSDESC relocations.
 * 
 * Problem: TLSDESC resolver returns an offset that caller adds to TPIDR_EL0.
 * But TPIDR_EL0 points to bionic TLS, not our storage.
 * 
 * Solution: Return a "fake offset" such that:
 *   TPIDR_EL0 + fake_offset = &our_tls_storage[real_offset]
 * ============================================================================ */

#define GLIBC_BRIDGE_DYNLIB_TLS_SIZE 65536  /* 64KB for dynamic library TLS */

/* Get the base address of our dynamic library TLS storage */
void* glibc_bridge_get_dynlib_tls_base(void);

/* TLSDESC resolver function - called by TLSDESC mechanism */
/* This is implemented in assembly, declared here for reference */
void glibc_bridge_tlsdesc_resolver_static(void);

/* C implementation called by the assembly resolver */
intptr_t glibc_bridge_tlsdesc_resolve_impl(void* desc);

#endif /* GLIBC_BRIDGE_TLS_H */

