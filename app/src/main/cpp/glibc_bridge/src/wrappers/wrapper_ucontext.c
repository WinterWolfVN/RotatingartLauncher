/*
 * glibc-bridge - ucontext Wrappers (User Context)
 * 
 * Android Bionic does NOT support ucontext functions.
 * These are stub implementations that return ENOSYS.
 * Programs using coroutines/fibers will not work properly.
 */

#define _GNU_SOURCE
#include <errno.h>
#include <signal.h>
#include <setjmp.h>

/* ============================================================================
 * Context switching - NOT supported on Android
 * ============================================================================ */

int getcontext_wrapper(void* ucp) {
    (void)ucp;
    errno = ENOSYS;
    return -1;
}

int setcontext_wrapper(const void* ucp) {
    (void)ucp;
    errno = ENOSYS;
    return -1;
}

int swapcontext_wrapper(void* oucp, const void* ucp) {
    (void)oucp;
    (void)ucp;
    errno = ENOSYS;
    return -1;
}

void makecontext_wrapper(void* ucp, void (*func)(void), int argc, ...) {
    (void)ucp;
    (void)func;
    (void)argc;
    /* Stub - cannot implement without kernel support */
}

/* ============================================================================
 * sigsetjmp extension
 * ============================================================================ */

int sigsetjmp_wrapper(sigjmp_buf env, int savemask) {
    return sigsetjmp(env, savemask);
}








