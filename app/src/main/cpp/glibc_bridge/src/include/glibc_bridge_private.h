/*
 * glibc-bridge - Internal Private Header
 * 
 * Internal structures and functions not exposed in public API
 */

#ifndef GLIBC_BRIDGE_PRIVATE_H
#define GLIBC_BRIDGE_PRIVATE_H

#include "glibc_bridge_api.h"
#include <elf.h>
#include <sys/types.h>

/* Include TLS compatibility layer */
#include "../glibc_bridge_tls.h"

/* ============================================================================
 * Internal Structures
 * ============================================================================ */

/* ELF Header wrapper */
typedef struct elfheader_s {
    char*           path;           /* File path */
    Elf64_Ehdr      ehdr;           /* ELF header */
    Elf64_Phdr*     phdr;           /* Program headers */
    int             phnum;          /* Number of program headers */
    
    void*           image;          /* Loaded image base */
    uintptr_t       delta;          /* Load address delta (ASLR) */
    size_t          memsz;          /* Total memory size */
    uintptr_t       entrypoint;     /* Entry point address */
    
    /* TLS information */
    size_t          tlssize;        /* TLS segment size */
    size_t          tlsalign;       /* TLS alignment */
    void*           tlsdata;        /* TLS initial data */
    
    /* Dynamic linking */
    char*           interp;         /* Interpreter path (ld-linux) */
    
    /* Flags */
    uint8_t         is_pie;         /* Position independent */
    uint8_t         is_static;      /* Statically linked */
} elfheader_t;

/* glibc TLS structure */
typedef struct glibc_tls_s {
    void*           tls_block;      /* Allocated TLS block */
    size_t          tls_size;       /* Total size */
    void*           tcb;            /* TCB pointer (tpidr_el0) */
} glibc_tls_t;

/* glibc-bridge runtime context */
struct glibc_bridge_s {
    glibc_bridge_config_t  config;         /* Configuration */
    
    /* Loaded ELFs */
    elfheader_t**   elfs;           /* Array of loaded ELFs */
    int             elf_count;      /* Number of loaded ELFs */
    int             elf_capacity;   /* Capacity of array */
    
    /* Execution state */
    void*           stack;          /* Allocated stack */
    size_t          stack_size;     /* Stack size */
    glibc_tls_t*    tls;            /* TLS state */
    
    /* Output capture */
    char*           stdout_buf;     /* Captured stdout */
    size_t          stdout_len;
    size_t          stdout_cap;
    char*           stderr_buf;     /* Captured stderr */
    size_t          stderr_len;
    size_t          stderr_cap;
};

/* Loaded ELF handle */
struct glibc_bridge_elf_s {
    glibc_bridge_t         bta;            /* Parent runtime */
    elfheader_t*    elf;            /* ELF header */
    int             loaded;         /* Is loaded into memory */
};

/* ============================================================================
 * Internal Functions - ELF Loading
 * ============================================================================ */

/* Parse ELF header */
elfheader_t* elf_parse_header(const char* path);

/* Load ELF into memory */
int elf_load_memory(elfheader_t* elf);

/* Relocate ELF */
int elf_relocate(elfheader_t* elf);

/* Relocate dynamic ELF using wrapper-based linking */
int glibc_bridge_relocate_dynamic(elfheader_t* elf);

/* Set symbol context for internal symbol resolution */
void glibc_bridge_set_symbol_context(elfheader_t* elf, Elf64_Sym* symtab, 
                               const char* strtab, size_t symcount);

/* Resolve symbol (returns wrapper, bionic, or internal function) */
void* glibc_bridge_resolve_symbol(const char* name);

/* Free ELF header */
void elf_free(elfheader_t* elf);

/* ============================================================================
 * Internal Functions - Execution
 * ============================================================================ */

/* Setup program stack (Linux ABI) */
uintptr_t setup_stack(void* stack_base, size_t stack_size,
                      int argc, char** argv, char** envp,
                      elfheader_t* elf);

/* Setup glibc-compatible TLS */
glibc_tls_t* setup_glibc_tls(elfheader_t* elf);

/* Free TLS */
void free_glibc_tls(glibc_tls_t* tls);

/* Set TLS register (tpidr_el0) */
void set_tls_register(void* tcb);

/* Jump to entry point (does not return) */
__attribute__((noreturn))
void jump_to_entry(uintptr_t entry, uintptr_t sp);

/* Run ELF in forked process with output capture */
int run_elf_forked(glibc_bridge_t bta, elfheader_t* elf,
                   int argc, char** argv, char** envp,
                   glibc_bridge_result_t* result);

/* Run ELF directly in current process (no fork) - Required for JNI compatibility */
int run_elf_direct(glibc_bridge_t bta, elfheader_t* elf,
                   int argc, char** argv, char** envp,
                   glibc_bridge_result_t* result);

/* Exit handler for direct execution mode */
void glibc_bridge_exit_handler(int code);
int glibc_bridge_exit_handler_active(void);

/* ============================================================================
 * Internal Functions - Memory
 * ============================================================================ */

/* Allocate executable memory */
void* alloc_exec_memory(size_t size, uintptr_t hint);

/* Free memory */
void free_memory(void* ptr, size_t size);

/* Allocate stack */
void* alloc_stack(size_t size);

/* Free stack */
void free_stack(void* stack, size_t size);

/* ============================================================================
 * Internal Functions - Logging
 * ============================================================================ */

/* Internal log levels */
#define GLIBC_BRIDGE_LOG_LVL_ERROR   1
#define GLIBC_BRIDGE_LOG_LVL_WARN    2
#define GLIBC_BRIDGE_LOG_LVL_INFO    3
#define GLIBC_BRIDGE_LOG_LVL_DEBUG   4

/* Current log level (set via glibc_bridge_set_log_level) */
extern int g_glibc_bridge_log_level;

/* Log macros */
#ifdef __ANDROID__
#include <android/log.h>
#define GLIBC_BRIDGE_LOG_TAG "glibc-bridge"
#define GLIBC_BRIDGE_LOG(level, fmt, ...) do { \
    if (level <= g_glibc_bridge_log_level) { \
        int prio = ANDROID_LOG_INFO; \
        if (level == GLIBC_BRIDGE_LOG_LVL_ERROR) prio = ANDROID_LOG_ERROR; \
        else if (level == GLIBC_BRIDGE_LOG_LVL_WARN) prio = ANDROID_LOG_WARN; \
        else if (level == GLIBC_BRIDGE_LOG_LVL_DEBUG) prio = ANDROID_LOG_DEBUG; \
        __android_log_print(prio, GLIBC_BRIDGE_LOG_TAG, fmt, ##__VA_ARGS__); \
    } \
} while(0)
#else
#include <stdio.h>
#define GLIBC_BRIDGE_LOG(level, fmt, ...) do { \
    if (level <= g_glibc_bridge_log_level) { \
        const char* lvl = "INFO"; \
        if (level == GLIBC_BRIDGE_LOG_LVL_ERROR) lvl = "ERROR"; \
        else if (level == GLIBC_BRIDGE_LOG_LVL_WARN) lvl = "WARN"; \
        else if (level == GLIBC_BRIDGE_LOG_LVL_DEBUG) lvl = "DEBUG"; \
        fprintf(stderr, "[glibc-bridge/%s] " fmt "\n", lvl, ##__VA_ARGS__); \
    } \
} while(0)
#endif

#define LOG_ERROR(fmt, ...) GLIBC_BRIDGE_LOG(GLIBC_BRIDGE_LOG_LVL_ERROR, fmt, ##__VA_ARGS__)
#define LOG_WARN(fmt, ...)  GLIBC_BRIDGE_LOG(GLIBC_BRIDGE_LOG_LVL_WARN, fmt, ##__VA_ARGS__)
#define LOG_INFO(fmt, ...)  GLIBC_BRIDGE_LOG(GLIBC_BRIDGE_LOG_LVL_INFO, fmt, ##__VA_ARGS__)
#define LOG_DEBUG(fmt, ...) GLIBC_BRIDGE_LOG(GLIBC_BRIDGE_LOG_LVL_DEBUG, fmt, ##__VA_ARGS__)

#endif /* GLIBC_BRIDGE_PRIVATE_H */

