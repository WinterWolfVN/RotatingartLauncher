/*
 * glibc-bridge PRoot-style Android Bypass Module
 * 
 * Implements proot/fake_id0 style bypasses for Android restrictions.
 * This module helps emulate operations that require root privileges
 * or are restricted on Android's security model.
 * 
 * Based on proot's fake_id0 extension.
 */

#ifndef GLIBC_BRIDGE_PROOT_BYPASS_H
#define GLIBC_BRIDGE_PROOT_BYPASS_H

#include <sys/types.h>
#include <sys/stat.h>
#include <signal.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>

/* ============================================================================
 * Fake Root Configuration
 * ============================================================================ */

typedef struct {
    uid_t ruid;     /* Real UID */
    uid_t euid;     /* Effective UID */
    uid_t suid;     /* Saved UID */
    gid_t rgid;     /* Real GID */
    gid_t egid;     /* Effective GID */
    gid_t sgid;     /* Saved GID */
    int fake_root;  /* 1 if pretending to be root */
} proot_config_t;

/* Global configuration */
extern proot_config_t g_proot_config;
extern int g_proot_bypass_enabled;

/* ============================================================================
 * Initialization
 * ============================================================================ */

/* Initialize proot bypass with fake root mode */
void proot_bypass_init(int fake_root);

/* Enable/disable bypass */
void proot_bypass_enable(int enable);

/* ============================================================================
 * Bypass Functions
 * ============================================================================ */

/* mkfifo with proot bypass - creates regular file on EPERM */
int proot_mkfifo(const char* pathname, mode_t mode);

/* mknod with proot bypass - emulates device files */
int proot_mknod(const char* pathname, mode_t mode, dev_t dev);

/* mknodat with proot bypass */
int proot_mknodat(int dirfd, const char* pathname, mode_t mode, dev_t dev);

/* getsid with fallback to getpid */
pid_t proot_getsid(pid_t pid);

/* getopt with proper global state handling */
int proot_getopt(int argc, char* const argv[], const char* optstring);

/* select with Android stdin handling */
int proot_select(int nfds, fd_set *readfds, fd_set *writefds,
                 fd_set *exceptfds, struct timeval *timeout);

/* pselect with Android stdin handling */
int proot_pselect(int nfds, fd_set *readfds, fd_set *writefds,
                  fd_set *exceptfds, const struct timespec *timeout,
                  const sigset_t *sigmask);

/* fcntl with flag translation */
int proot_fcntl(int fd, int cmd, ...);

/* setsockopt with Android bypass */
int proot_setsockopt(int sockfd, int level, int optname,
                     const void *optval, socklen_t optlen);

/* getsockopt with Android bypass */
int proot_getsockopt(int sockfd, int level, int optname,
                     void *optval, socklen_t *optlen);

/* ============================================================================
 * UID/GID Emulation (proot fake_id0 style)
 * ============================================================================ */

/* Get emulated UIDs */
uid_t proot_getuid(void);
uid_t proot_geteuid(void);
uid_t proot_getgid(void);
uid_t proot_getegid(void);

/* Set emulated UIDs (always succeeds in fake root mode) */
int proot_setuid(uid_t uid);
int proot_seteuid(uid_t euid);
int proot_setgid(gid_t gid);
int proot_setegid(gid_t egid);

/* ============================================================================
 * File System Emulation
 * ============================================================================ */

/* chmod with proot bypass (ignores EPERM in fake root) */
int proot_chmod(const char* pathname, mode_t mode);

/* chown with proot bypass (ignores EPERM in fake root) */
int proot_chown(const char* pathname, uid_t owner, gid_t group);

/* ============================================================================
 * Locale/iconv Emulation
 * ============================================================================ */

/* Stub iconv_open that returns a valid handle */
void* proot_iconv_open(const char* tocode, const char* fromcode);

/* Stub iconv that does passthrough */
size_t proot_iconv(void* cd, char** inbuf, size_t* inbytesleft,
                   char** outbuf, size_t* outbytesleft);

/* Stub iconv_close */
int proot_iconv_close(void* cd);

#endif /* GLIBC_BRIDGE_PROOT_BYPASS_H */





