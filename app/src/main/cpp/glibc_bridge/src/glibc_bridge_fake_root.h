/*
 * glibc-bridge - Fake Root Layer
 * 
 * Implements proot-style fake root functionality without ptrace.
 * Works by intercepting libc calls at the wrapper level.
 * 
 * Features:
 * - Fake UID/GID (pretend to be root)
 * - Permission bypass for certain operations
 * - Signal handling fixes
 * - Socket permission workarounds
 */

#ifndef GLIBC_BRIDGE_FAKE_ROOT_H
#define GLIBC_BRIDGE_FAKE_ROOT_H

#include <sys/types.h>
#include <sys/stat.h>

/* ============================================================================
 * Configuration
 * ============================================================================ */

/* Enable/disable fake root mode */
extern int g_fake_root_enabled;

/* Fake UID/GID values (default: 0 = root) */
extern uid_t g_fake_uid;
extern gid_t g_fake_gid;

/* Initialize fake root mode */
void glibc_bridge_fake_root_init(void);

/* ============================================================================
 * Fake Identity Functions
 * ============================================================================ */

/* Return fake UID (0 if fake root enabled) */
uid_t glibc_bridge_fake_getuid(void);
uid_t glibc_bridge_fake_geteuid(void);
gid_t glibc_bridge_fake_getgid(void);
gid_t glibc_bridge_fake_getegid(void);

/* Setuid/setgid - always succeed in fake root mode */
int glibc_bridge_fake_setuid(uid_t uid);
int glibc_bridge_fake_setgid(gid_t gid);
int glibc_bridge_fake_seteuid(uid_t euid);
int glibc_bridge_fake_setegid(gid_t egid);

/* ============================================================================
 * Stat Manipulation
 * ============================================================================ */

/* Modify stat buffer to show fake ownership */
void glibc_bridge_fake_stat_ownership(struct stat* buf);

/* ============================================================================
 * Permission Bypass
 * ============================================================================ */

/* Check if we should bypass permission error */
int glibc_bridge_should_bypass_permission(int error_code, const char* operation);

/* Fake successful return for permission-related syscalls */
int glibc_bridge_fake_permission_success(const char* operation);

/* ============================================================================
 * Signal Handling Fixes
 * ============================================================================ */

/* Setup signal handling for forked process */
void glibc_bridge_setup_signals(void);

/* Signal handler that works in forked Android process */
typedef void (*glibc_bridge_signal_handler_t)(int);
glibc_bridge_signal_handler_t glibc_bridge_signal(int signum, glibc_bridge_signal_handler_t handler);
int glibc_bridge_raise(int sig);

/* ============================================================================
 * Socket Permission Workaround
 * ============================================================================ */

/* Try alternative socket creation methods */
int glibc_bridge_socket_with_fallback(int domain, int type, int protocol);

/* ============================================================================
 * Capability Emulation (minimal)
 * ============================================================================ */

/* Fake capability checks */
int glibc_bridge_fake_capget(void* hdrp, void* datap);
int glibc_bridge_fake_capset(void* hdrp, const void* datap);

#endif /* GLIBC_BRIDGE_FAKE_ROOT_H */








