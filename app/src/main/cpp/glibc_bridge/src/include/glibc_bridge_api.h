/*
 * glibc-bridge - glibc to Bionic Compatibility Layer
 * 
 * Public API for running Linux ARM64 glibc executables on Android
 * 
 * Copyright (c) 2024
 * Licensed under MIT License
 */

#ifndef GLIBC_BRIDGE_API_H
#define GLIBC_BRIDGE_API_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ============================================================================
 * Version Information
 * ============================================================================ */

#define GLIBC_BRIDGE_VERSION_MAJOR     1
#define GLIBC_BRIDGE_VERSION_MINOR     0
#define GLIBC_BRIDGE_VERSION_PATCH     0
#define GLIBC_BRIDGE_VERSION_STRING    "1.0.0"

/* ============================================================================
 * Error Codes
 * ============================================================================ */

typedef enum {
    GLIBC_BRIDGE_OK                    = 0,
    GLIBC_BRIDGE_ERROR_INVALID_ARG     = -1,
    GLIBC_BRIDGE_ERROR_FILE_NOT_FOUND  = -2,
    GLIBC_BRIDGE_ERROR_INVALID_ELF     = -3,
    GLIBC_BRIDGE_ERROR_LOAD_FAILED     = -4,
    GLIBC_BRIDGE_ERROR_EXEC_FAILED     = -5,
    GLIBC_BRIDGE_ERROR_OUT_OF_MEMORY   = -6,
    GLIBC_BRIDGE_ERROR_NOT_SUPPORTED   = -7,
    GLIBC_BRIDGE_ERROR_FORK_FAILED     = -8,
    GLIBC_BRIDGE_ERROR_SIGNAL          = -100,  /* Add signal number to get specific signal */
} glibc_bridge_error_t;

/* ============================================================================
 * Log Levels
 * ============================================================================ */

typedef enum {
    GLIBC_BRIDGE_LOG_NONE      = 0,
    GLIBC_BRIDGE_LOG_ERROR     = 1,
    GLIBC_BRIDGE_LOG_WARN      = 2,
    GLIBC_BRIDGE_LOG_INFO      = 3,
    GLIBC_BRIDGE_LOG_DEBUG     = 4,
} glibc_bridge_log_level_t;

/* ============================================================================
 * Configuration
 * ============================================================================ */

typedef struct glibc_bridge_config_s {
    glibc_bridge_log_level_t   log_level;          /* Logging verbosity */
    size_t              stack_size;          /* Stack size (default: 8MB) */
    int                 redirect_output;     /* Redirect stdout/stderr to logcat */
    int                 use_tls;             /* Setup glibc-compatible TLS */
    const char*         lib_path;           /* Library search path for dynamic linking */
    int                 direct_execution;    /* Run ELF directly without fork (required for JNI) */
} glibc_bridge_config_t;

/* Default configuration */
#define GLIBC_BRIDGE_CONFIG_DEFAULT { \
    .log_level = GLIBC_BRIDGE_LOG_INFO, \
    .stack_size = 32 * 1024 * 1024, \
    .redirect_output = 1, \
    .use_tls = 1, \
    .lib_path = NULL, \
    .direct_execution = 1 \
}

/* ============================================================================
 * Opaque Handle Types
 * ============================================================================ */

typedef struct glibc_bridge_s*         glibc_bridge_t;        /* glibc-bridge runtime handle */
typedef struct glibc_bridge_elf_s*     glibc_bridge_elf_t;    /* Loaded ELF handle */

/* ============================================================================
 * ELF Information
 * ============================================================================ */

typedef struct glibc_bridge_elf_info_s {
    const char*     path;           /* File path */
    uint8_t         is_64bit;       /* 1 = 64-bit, 0 = 32-bit */
    uint8_t         is_arm64;       /* 1 = ARM64/AArch64 */
    uint8_t         is_static;      /* 1 = statically linked */
    uint8_t         is_pie;         /* 1 = position independent */
    uintptr_t       entry_point;    /* Entry point address */
    uintptr_t       load_addr;      /* Load address */
    size_t          memory_size;    /* Total memory footprint */
} glibc_bridge_elf_info_t;

/* ============================================================================
 * Execution Result
 * ============================================================================ */

typedef struct glibc_bridge_result_s {
    int             exit_code;      /* Exit code if exited normally */
    int             signal;         /* Signal number if killed by signal */
    int             exited;         /* 1 = exited normally, 0 = killed by signal */
    char*           stdout_buf;     /* Captured stdout (if redirect_output) */
    size_t          stdout_len;     /* Length of stdout */
    char*           stderr_buf;     /* Captured stderr (if redirect_output) */
    size_t          stderr_len;     /* Length of stderr */
} glibc_bridge_result_t;

/* ============================================================================
 * Core API Functions
 * ============================================================================ */

/**
 * Get glibc-bridge version string
 * @return Version string (e.g., "1.0.0")
 */
const char* glibc_bridge_version(void);

/**
 * Initialize glibc-bridge runtime
 * @param config Configuration (NULL for defaults)
 * @return glibc-bridge handle or NULL on error
 */
glibc_bridge_t glibc_bridge_init(const glibc_bridge_config_t* config);

/**
 * Cleanup glibc-bridge runtime and free resources
 * @param bridge glibc-bridge handle
 */
void glibc_bridge_cleanup(glibc_bridge_t bridge);

/**
 * Set log level
 * @param bridge glibc-bridge handle
 * @param level Log level
 */
void glibc_bridge_set_log_level(glibc_bridge_t bridge, glibc_bridge_log_level_t level);

/**
 * Set library search path (for dynamic linked programs)
 * @param bridge glibc-bridge handle
 * @param lib_path Path to directory containing glibc libraries
 */
void glibc_bridge_set_lib_path(glibc_bridge_t bridge, const char* lib_path);

/* ============================================================================
 * ELF Loading API
 * ============================================================================ */

/**
 * Load an ELF executable
 * @param bridge glibc-bridge handle
 * @param path Path to the ELF file
 * @return ELF handle or NULL on error
 */
glibc_bridge_elf_t glibc_bridge_load(glibc_bridge_t bridge, const char* path);

/**
 * Get information about loaded ELF
 * @param elf ELF handle
 * @param info Output info structure
 * @return GLIBC_BRIDGE_OK on success
 */
glibc_bridge_error_t glibc_bridge_elf_info(glibc_bridge_elf_t elf, glibc_bridge_elf_info_t* info);

/**
 * Unload an ELF and free resources
 * @param elf ELF handle
 */
void glibc_bridge_unload(glibc_bridge_elf_t elf);

/* ============================================================================
 * Execution API
 * ============================================================================ */

/**
 * Run a loaded ELF executable
 * @param bridge glibc-bridge handle
 * @param elf ELF handle
 * @param argc Argument count
 * @param argv Argument vector (can be NULL if argc=0)
 * @param envp Environment variables (NULL = inherit)
 * @param result Output result (can be NULL)
 * @return Exit code or negative error code
 */
int glibc_bridge_run(glibc_bridge_t bridge, glibc_bridge_elf_t elf, 
              int argc, char** argv, char** envp,
              glibc_bridge_result_t* result);

/**
 * Convenience function: Load and run an ELF in one call
 * @param bridge glibc-bridge handle
 * @param path Path to ELF file
 * @param argc Argument count
 * @param argv Argument vector
 * @param envp Environment variables (NULL = inherit)
 * @param result Output result (can be NULL)
 * @return Exit code or negative error code
 */
int glibc_bridge_exec(glibc_bridge_t bridge, const char* path,
               int argc, char** argv, char** envp,
               glibc_bridge_result_t* result);

/**
 * Free result buffers
 * @param result Result structure to free
 */
void glibc_bridge_result_free(glibc_bridge_result_t* result);

/* ============================================================================
 * Main Entry Point - Auto-initialization
 * ============================================================================ */

/**
 * Execute glibc program - Main entry point
 * 
 * This function automatically initializes glibc-bridge if not already initialized.
 * All initialization is handled internally by the compatibility layer.
 * 
 * @param path Path to ELF file
 * @param argc Argument count
 * @param argv Argument vector
 * @param envp Environment variables (NULL = inherit)
 * @param rootfs_path Root filesystem path (NULL = use default)
 * @return Exit code or negative error code
 */
int glibc_bridge_execute(const char* path, int argc, char** argv, char** envp, const char* rootfs_path);

/* ============================================================================
 * Utility Functions
 * ============================================================================ */

/**
 * Check if a file is a valid ELF
 * @param path Path to file
 * @return 1 if valid ELF, 0 otherwise
 */
int glibc_bridge_is_valid_elf(const char* path);

/**
 * Get error message for error code
 * @param error Error code
 * @return Error message string
 */
const char* glibc_bridge_strerror(glibc_bridge_error_t error);

#ifdef __cplusplus
}
#endif

#endif /* GLIBC_BRIDGE_API_H */

