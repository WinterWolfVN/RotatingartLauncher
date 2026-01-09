/*
 * glibc-bridge Dynamic Linker - Main Entry Point
 * 
 * This is the main dynamic linker module that coordinates:
 *   - Symbol resolution (via glibc_bridge_resolver.c)
 *   - Relocation processing (via glibc_bridge_reloc.c)
 *   - Wrapper function registration (via wrapper_*.c modules)
 * 
 * Instead of loading real glibc libraries, we intercept symbol
 * lookups and redirect them to bionic or our wrapper implementations.
 * This is similar to what libhybris does.
 * 
 * Module Structure:
 * ================
 * 
 * glibc_bridge_dynlink.c (this file)
 *   └── Entry point and initialization
 * 
 * dynlink/
 *   ├── glibc_bridge_log.c          - Logging system with env var control
 *   ├── glibc_bridge_symbol_table.c - Symbol wrapper table definition
 *   ├── glibc_bridge_resolver.c     - Symbol resolution logic
 *   └── glibc_bridge_reloc.c        - ELF relocation processing
 * 
 * wrappers/
 *   ├── wrapper_libc.c       - Basic libc wrappers
 *   ├── wrapper_stat.c       - stat/fstat wrappers
 *   ├── wrapper_locale.c     - Locale _l suffix functions
 *   ├── wrapper_fortify.c    - FORTIFY _chk suffix functions
 *   ├── wrapper_gettext.c    - Internationalization stubs
 *   └── wrapper_cxx.c        - C++ runtime wrappers
 * 
 * glibc_bridge_stdio.c              - FILE structure conversion (existing)
 * glibc_bridge_tls.c                - TLS and ctype wrappers (existing)
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "include/glibc_bridge_private.h"
#include "include/glibc_bridge_wrappers.h"
#include "dynlink/glibc_bridge_log.h"

/* ============================================================================
 * Module Initialization
 * 
 * Called automatically when the dynamic linker module is first used.
 * Performs one-time setup of the wrapper system.
 * ============================================================================ */

static int g_dynlink_initialized = 0;

static void __attribute__((constructor)) glibc_bridge_dynlink_init(void) {
    if (g_dynlink_initialized) return;
    g_dynlink_initialized = 1;
    
    /* Log initialization */
    if (glibc_bridge_dl_get_log_level() >= GLIBC_BRIDGE_DL_LOG_DEBUG) {
        const char* msg = "[DYNLINK] glibc-bridge Dynamic Linker initialized\n";
        write(STDERR_FILENO, msg, strlen(msg));
    }
}

/* ============================================================================
 * Public API Re-exports
 * 
 * These functions are the main interface used by the glibc-bridge runtime.
 * They delegate to the appropriate submodules.
 * ============================================================================ */

/*
 * The following functions are implemented in their respective modules:
 * 
 * glibc_bridge_resolve_symbol()       - dynlink/glibc_bridge_resolver.c
 * glibc_bridge_relocate_dynamic()     - dynlink/glibc_bridge_reloc.c
 * glibc_bridge_set_symbol_context()   - dynlink/glibc_bridge_resolver.c
 * glibc_bridge_get_symbol_table()     - dynlink/glibc_bridge_symbol_table.c
 * 
 * All wrapper functions are in wrappers/*.c
 * 
 * See include/glibc_bridge_wrappers.h for the complete API.
 */

/* ============================================================================
 * Version Information
 * ============================================================================ */

const char* glibc_bridge_dynlink_version(void) {
    return "glibc-bridge Dynamic Linker v1.0.0 (Modular)";
}

/* ============================================================================
 * Debug/Diagnostic Functions
 * ============================================================================ */

/**
 * Print summary of loaded wrapper counts
 */
void glibc_bridge_dynlink_print_stats(void) {
    if (glibc_bridge_dl_get_log_level() < GLIBC_BRIDGE_DL_LOG_INFO) return;
    
    const symbol_wrapper_t* table = glibc_bridge_get_symbol_table();
    int total = 0;
    int with_wrapper = 0;
    int passthrough = 0;
    
    for (const symbol_wrapper_t* w = table; w->name; w++) {
        total++;
        if (w->wrapper) {
            with_wrapper++;
        } else {
            passthrough++;
        }
    }
    
    char buf[256];
    snprintf(buf, sizeof(buf), 
             "[DYNLINK] Symbol table: %d total, %d wrappers, %d pass-through\n",
             total, with_wrapper, passthrough);
        write(STDERR_FILENO, buf, strlen(buf));
    }
    
/**
 * Dump all registered symbols (for debugging)
 */
void glibc_bridge_dynlink_dump_symbols(void) {
    if (glibc_bridge_dl_get_log_level() < GLIBC_BRIDGE_DL_LOG_DEBUG) return;
    
    const symbol_wrapper_t* table = glibc_bridge_get_symbol_table();
    
    write(STDERR_FILENO, "[DYNLINK] Registered symbols:\n", 30);
    
    for (const symbol_wrapper_t* w = table; w->name; w++) {
        char buf[128];
        snprintf(buf, sizeof(buf), "  %s -> %s\n", 
                 w->name, 
                 w->wrapper ? "wrapper" : "bionic");
        write(STDERR_FILENO, buf, strlen(buf));
    }
}
