/*
 * glibc-bridge Dynamic Linker - Logging System
 * 
 * Environment variable controlled logging with multiple levels.
 * Set GLIBC_BRIDGE_LOG_LEVEL to control verbosity (0-5).
 */

#ifndef GLIBC_BRIDGE_LOG_H
#define GLIBC_BRIDGE_LOG_H

#ifdef __cplusplus
extern "C" {
#endif

/* ============================================================================
 * Log Levels
 * 
 * Set GLIBC_BRIDGE_LOG_LEVEL environment variable:
 *   0 = NONE (no logs)
 *   1 = ERROR only
 *   2 = WARN + ERROR
 *   3 = INFO + WARN + ERROR (default)
 *   4 = DEBUG + INFO + WARN + ERROR
 *   5 = TRACE (everything including symbol resolution)
 * ============================================================================ */

#define GLIBC_BRIDGE_DL_LOG_NONE   0
#define GLIBC_BRIDGE_DL_LOG_ERROR  1
#define GLIBC_BRIDGE_DL_LOG_WARN   2
#define GLIBC_BRIDGE_DL_LOG_INFO   3
#define GLIBC_BRIDGE_DL_LOG_DEBUG  4
#define GLIBC_BRIDGE_DL_LOG_TRACE  5

/**
 * Get current log level
 * Reads GLIBC_BRIDGE_LOG_LEVEL env var on first call, caches result
 * @return Current log level (0-5)
 */
int glibc_bridge_dl_get_log_level(void);

/**
 * Force set log level (bypasses cache)
 * @param level Log level (0-5)
 */
void glibc_bridge_dl_set_log_level(int level);

/**
 * Log a message at specified level
 * @param level Log level
 * @param msg Message to log
 */
void glibc_bridge_dl_log(int level, const char* msg);

/* Convenience logging functions */
void glibc_bridge_dl_log_error(const char* msg);
void glibc_bridge_dl_log_warn(const char* msg);
void glibc_bridge_dl_log_info(const char* msg);
void glibc_bridge_dl_log_debug(const char* msg);
void glibc_bridge_dl_log_trace(const char* msg);

/**
 * Helper to write debug message in child process (async-signal-safe)
 * Uses only write() syscall
 */
void glibc_bridge_dl_child_log(const char *msg);

#ifdef __cplusplus
}
#endif

#endif /* GLIBC_BRIDGE_LOG_H */



