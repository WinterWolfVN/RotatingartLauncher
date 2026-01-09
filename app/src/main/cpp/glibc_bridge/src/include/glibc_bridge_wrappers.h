/*
 * glibc-bridge Wrapper Functions Header
 * 
 * Declares all glibc-to-bionic wrapper functions.
 * These functions bridge the gap between glibc ABI and bionic.
 */

#ifndef GLIBC_BRIDGE_WRAPPERS_H
#define GLIBC_BRIDGE_WRAPPERS_H

#include <stdio.h>
#include <stdarg.h>
#include <wchar.h>
#include <wctype.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <time.h>
#include <pthread.h>
#include <setjmp.h>
#include <sched.h>
#include <search.h>
#include <dlfcn.h>

/* cpu_set_t compatibility - Android NDK may or may not define it */
#ifdef __ANDROID__
/* Check if cpu_set_t is defined by checking for CPU_ZERO macro */
#ifndef CPU_ZERO
/* Define cpu_set_t if CPU macros are not available */
#ifndef _CPU_SET_T_DEFINED
#define _CPU_SET_T_DEFINED
typedef struct {
    unsigned long __bits[1024 / (8 * sizeof(unsigned long))];
} cpu_set_t;
#endif
#endif
#endif

/* off64_t compatibility - use int64_t if not defined */
#include <stdint.h>
#ifndef __off64_t_defined
#ifndef off64_t
typedef int64_t off64_t;
#endif
#endif

#ifdef __cplusplus
extern "C" {
#endif

/* ============================================================================
 * Wrapper Types
 * ============================================================================ */

/* Symbol wrapper entry */
typedef struct {
    const char* name;       /* glibc symbol name */
    void* wrapper;          /* Our wrapper function (NULL = use bionic directly) */
} symbol_wrapper_t;

/* Locale compatibility type */
typedef void* locale_t_compat;

/* ============================================================================
 * ctype Wrappers (glibc_bridge_tls.c)
 * ============================================================================ */

void* __ctype_b_loc_wrapper(void);
void* __ctype_tolower_loc_wrapper(void);
void* __ctype_toupper_loc_wrapper(void);

/* ============================================================================
 * errno Wrapper (glibc_bridge_tls.c)
 * ============================================================================ */

int* __errno_location_wrapper(void);

/* ============================================================================
 * Basic libc Wrappers (wrapper_libc.c)
 * ============================================================================ */

char* secure_getenv_wrapper(const char* name);
int __register_atfork_wrapper(void (*prepare)(void), void (*parent)(void), 
                               void (*child)(void), void* dso_handle);
void error_wrapper(int status, int errnum, const char* format, ...);

/* __libc_start_main wrapper - critical for program startup */
int __libc_start_main_wrapper(
    int (*main_func)(int, char**, char**),
    int argc,
    char** argv,
    int (*init)(int, char**, char**),
    void (*fini)(void),
    void (*rtld_fini)(void),
    void* stack_end);

/* Assert wrapper */
void assert_fail_wrapper(const char* assertion, const char* file, 
                         unsigned int line, const char* function);

/* pthread wrapper */
int pthread_create_wrapper(pthread_t* thread, const pthread_attr_t* attr,
                          void* (*start_routine)(void*), void* arg);
int pthread_key_create_wrapper(pthread_key_t* key, void (*destructor)(void*));

/* dl_find_object stub */
int dl_find_object_wrapper(void* addr, void* result);

/* dl_iterate_phdr wrapper - for exception handling and stack unwinding */
struct dl_phdr_info;  /* Forward declaration */
int dl_iterate_phdr_wrapper(
    int (*callback)(struct dl_phdr_info* info, size_t size, void* data),
    void* data);

/* ============================================================================
 * stat Wrappers (wrapper_stat.c)
 * ============================================================================ */

/* glibc __xstat family */
int __fxstat64_wrapper(int ver, int fd, void* buf);
int __xstat64_wrapper(int ver, const char* path, void* buf);
int __lxstat64_wrapper(int ver, const char* path, void* buf);
int __fxstatat64_wrapper(int ver, int dirfd, const char* path, 
                         void* buf, int flags);

/* Direct stat wrappers */
int stat_wrapper(const char* path, void* buf);
int fstat_wrapper(int fd, void* buf);
int lstat_wrapper(const char* path, void* buf);
int fstatat_wrapper(int dirfd, const char* path, void* buf, int flags);
int stat64_wrapper(const char* path, void* buf);
int fstat64_wrapper(int fd, void* buf);
int lstat64_wrapper(const char* path, void* buf);
int fstatat64_wrapper(int dirfd, const char* path, void* buf, int flags);

/* statfs/statvfs */
struct statfs;
struct statvfs;
int statfs_wrapper(const char* path, struct statfs* buf);
int fstatfs_wrapper(int fd, struct statfs* buf);
int statfs64_wrapper(const char* path, struct statfs* buf);
int fstatfs64_wrapper(int fd, struct statfs* buf);
int statvfs_wrapper(const char* path, struct statvfs* buf);
int fstatvfs_wrapper(int fd, struct statvfs* buf);
int statvfs64_wrapper(const char* path, struct statvfs* buf);
int fstatvfs64_wrapper(int fd, struct statvfs* buf);

/* Path operations */
char* realpath_wrapper(const char* path, char* resolved_path);
ssize_t readlink_wrapper(const char* path, char* buf, size_t bufsiz);
ssize_t readlinkat_wrapper(int dirfd, const char* path, char* buf, size_t bufsiz);

/* Access */
int access_wrapper(const char* path, int mode);
int faccessat_wrapper(int dirfd, const char* path, int mode, int flags);

/* Permissions */
int chmod_wrapper(const char* path, mode_t mode);
int fchmod_wrapper(int fd, mode_t mode);
int fchmodat_wrapper(int dirfd, const char* path, mode_t mode, int flags);
int chown_wrapper(const char* path, uid_t owner, gid_t group);
int fchown_wrapper(int fd, uid_t owner, gid_t group);
int fchownat_wrapper(int dirfd, const char* path, uid_t owner, gid_t group, int flags);
int lchown_wrapper(const char* path, uid_t owner, gid_t group);

/* File open/create */
int openat_wrapper(int dirfd, const char* path, int flags, ...);
int creat_wrapper(const char* path, mode_t mode);
int creat64_wrapper(const char* path, mode_t mode);

/* Directory operations */
int mkdir_wrapper(const char* path, mode_t mode);
int mkdirat_wrapper(int dirfd, const char* path, mode_t mode);
int rmdir_wrapper(const char* path);

/* Link operations */
int link_wrapper(const char* oldpath, const char* newpath);
int linkat_wrapper(int olddirfd, const char* oldpath, int newdirfd, 
                   const char* newpath, int flags);
int symlink_wrapper(const char* target, const char* linkpath);
int symlinkat_wrapper(const char* target, int newdirfd, const char* linkpath);
int unlink_wrapper(const char* path);
int unlinkat_wrapper(int dirfd, const char* path, int flags);
int rename_wrapper(const char* oldpath, const char* newpath);
int renameat_wrapper(int olddirfd, const char* oldpath, int newdirfd, const char* newpath);
int renameat2_wrapper(int olddirfd, const char* oldpath, int newdirfd, 
                      const char* newpath, unsigned int flags);

/* File descriptor operations */
int dup_wrapper(int oldfd);
int dup2_wrapper(int oldfd, int newfd);
int dup3_wrapper(int oldfd, int newfd, int flags);
int fcntl_wrapper(int fd, int cmd, ...);
int ftruncate_wrapper(int fd, off_t length);
int ftruncate64_wrapper(int fd, off64_t length);
int truncate_wrapper(const char* path, off_t length);
int truncate64_wrapper(const char* path, off64_t length);

/* Pipe */
int pipe_wrapper(int pipefd[2]);
int pipe2_wrapper(int pipefd[2], int flags);

/* Time modification */
int utimensat_wrapper(int dirfd, const char* path, 
                      const struct timespec times[2], int flags);
int futimens_wrapper(int fd, const struct timespec times[2]);

/* mkstemp family */
int mkstemp_wrapper(char* templ);
int mkostemp_wrapper(char* templ, int flags);
int mkstemp64_wrapper(char* templ);
char* mkdtemp_wrapper(char* templ);

/* Directory operations */
#include <dirent.h>
DIR* opendir_wrapper(const char* name);
DIR* fdopendir_wrapper(int fd);
int closedir_wrapper(DIR* dirp);
struct dirent* readdir_wrapper(DIR* dirp);
int readdir_r_wrapper(DIR* dirp, struct dirent* entry, struct dirent** result);
void rewinddir_wrapper(DIR* dirp);
void seekdir_wrapper(DIR* dirp, long loc);
long telldir_wrapper(DIR* dirp);
int dirfd_wrapper(DIR* dirp);
int scandir_wrapper(const char* dirp, struct dirent*** namelist,
                    int (*filter)(const struct dirent*),
                    int (*compar)(const struct dirent**, const struct dirent**));

/* open/fopen wrappers */
int open_wrapper(const char* pathname, int flags, ...);
int open64_wrapper(const char* pathname, int flags, ...);
FILE* fopen_wrapper(const char* pathname, const char* mode);
FILE* fopen64_wrapper(const char* pathname, const char* mode);
FILE* freopen_wrapper(const char* pathname, const char* mode, FILE* stream);
FILE* freopen64_wrapper(const char* pathname, const char* mode, FILE* stream);
void* tmpfile_wrapper(void);
void* tmpfile64_wrapper(void);

/* chdir wrapper */
int chdir_wrapper(const char* path);
int fchdir_wrapper(int fd);

/* ============================================================================
 * Memory/String Wrappers (wrapper_stat.c)
 * ============================================================================ */

int bcmp_wrapper(const void* s1, const void* s2, size_t n);
void bcopy_wrapper(const void* src, void* dest, size_t n);
void bzero_wrapper(void* s, size_t n);
void explicit_bzero_wrapper(void* s, size_t n);

/* getdelim/getline wrappers */
ssize_t getdelim_wrapper(char** lineptr, size_t* n, int delim, FILE* stream);
ssize_t getline_wrapper(char** lineptr, size_t* n, FILE* stream);

/* __fsetlocking wrapper - handles glibc/bionic FILE* incompatibility */
int __fsetlocking_wrapper(FILE* fp, int type);

/* popen/pclose wrappers */
FILE* popen_wrapper(const char* command, const char* type);
int pclose_wrapper(FILE* stream);

/* h_errno location */
int* __h_errno_location_wrapper(void);

/* Memory allocation */
void* valloc_wrapper(size_t size);
void* pvalloc_wrapper(size_t size);

/* Memory locking (wrapper_mlock.c) */
int mlock_wrapper(const void* addr, size_t len);
int munlock_wrapper(const void* addr, size_t len);
int mlockall_wrapper(int flags);
int munlockall_wrapper(void);
int madvise_wrapper(void* addr, size_t length, int advice);
int membarrier_wrapper(int cmd, unsigned int flags, int cpu_id);

/* Scheduler affinity (wrapper_mlock.c) */
#include <sched.h>
int sched_getaffinity_wrapper(pid_t pid, size_t cpusetsize, cpu_set_t* mask);
int sched_setaffinity_wrapper(pid_t pid, size_t cpusetsize, const cpu_set_t* mask);

/* syscall wrapper - intercept membarrier calls blocked by seccomp */
long syscall_wrapper(long number, ...);

/* pthread_mutex_init wrapper for debugging */
int pthread_mutex_init_wrapper(pthread_mutex_t* mutex, const pthread_mutexattr_t* attr);

/* String functions */
int strverscmp_wrapper(const char* s1, const char* s2);
char* __xpg_basename_wrapper(char* path);

/* wordexp */
struct wordexp_t;
int wordexp_wrapper(const char* words, void* pwordexp, int flags);
void wordfree_wrapper(void* pwordexp);

/* Weak symbol stubs */
void _ITM_deregisterTMCloneTable_stub(void);
void _ITM_registerTMCloneTable_stub(void);
void __gmon_start___stub(void);
void _Jv_RegisterClasses_stub(void* classes);

/* LTTng stubs (for .NET CoreCLR tracing) */
int lttng_probe_register_stub(void* probe);
void lttng_probe_unregister_stub(void* probe);

/* ============================================================================
 * scanf Family Wrappers (wrapper_libc.c)
 * ============================================================================ */

/* scanf wrappers take fixed args instead of varargs */
int __isoc99_sscanf_wrapper(const char* str, const char* format,
                            uint64_t a0, uint64_t a1, uint64_t a2, uint64_t a3);
int __isoc99_scanf_wrapper(const char* format,
                           uint64_t a0, uint64_t a1, uint64_t a2, 
                           uint64_t a3, uint64_t a4);
int __isoc99_fscanf_wrapper(FILE* stream, const char* format,
                            uint64_t a0, uint64_t a1, uint64_t a2, uint64_t a3);
int __isoc99_vsscanf_wrapper(const char* str, const char* format, va_list ap);
int __isoc99_vscanf_wrapper(const char* format, va_list ap);
int __isoc99_vfscanf_wrapper(FILE* stream, const char* format, va_list ap);

/* ============================================================================
 * stdio Wrappers (glibc_bridge_stdio.c)
 * FILE structure conversion wrappers
 * ============================================================================ */

/* Standard stream access - return address of FILE* variable (for stdout/stderr/stdin symbols) */
void* glibc_bridge_get_stdin(void);
void* glibc_bridge_get_stdout(void);
void* glibc_bridge_get_stderr(void);

/* Direct _IO_FILE structure access (for _IO_2_1_stdout_ etc symbols) */
void* glibc_bridge_get_glibc_stdin_struct(void);
void* glibc_bridge_get_glibc_stdout_struct(void);
void* glibc_bridge_get_glibc_stderr_struct(void);

void glibc_bridge_stdio_init(void);
FILE* glibc_bridge_get_bionic_fp(void* glibc_fp);  /* Convert glibc FILE* to bionic FILE* */

/* Note: fopen_wrapper, fopen64_wrapper, freopen_wrapper, freopen64_wrapper
 * are declared above in the "open/fopen wrappers" section */
int fclose_wrapper(void* stream);
size_t fread_wrapper(void* ptr, size_t size, size_t nmemb, void* stream);
char* fgets_wrapper(char* s, int size, void* stream);
int fgetc_wrapper(void* stream);
int getc_wrapper(void* stream);
int ungetc_wrapper(int c, void* stream);
size_t fwrite_wrapper(const void* ptr, size_t size, size_t nmemb, void* stream);
int fputs_wrapper(const char* s, void* stream);
int puts_wrapper(const char* s);
int printf_wrapper(const char* format, ...);
int vprintf_wrapper(const char* format, va_list ap);
int fputc_wrapper(int c, void* stream);
int putc_wrapper(int c, void* stream);
int fprintf_wrapper(void* stream, const char* format, ...);
int vfprintf_wrapper(void* stream, const char* format, va_list ap);
int fseek_wrapper(void* stream, long offset, int whence);
int fseeko_wrapper(void* stream, off_t offset, int whence);
int fseeko64_wrapper(void* stream, off64_t offset, int whence);
long ftell_wrapper(void* stream);
off_t ftello_wrapper(void* stream);
off64_t ftello64_wrapper(void* stream);
void rewind_wrapper(void* stream);
int fflush_wrapper(void* stream);
int feof_wrapper(void* stream);
int ferror_wrapper(void* stream);
void clearerr_wrapper(void* stream);
int fileno_wrapper(void* stream);
int setvbuf_wrapper(void* stream, char* buf, int mode, size_t size);
void setbuf_wrapper(void* stream, char* buf);
void flockfile_wrapper(void* stream);
void funlockfile_wrapper(void* stream);
int ftrylockfile_wrapper(void* stream);
int __uflow_wrapper(void* stream);
int __overflow_wrapper(void* stream, int c);

/* ============================================================================
 * Locale Wrappers (wrapper_locale.c)
 * ============================================================================ */

locale_t_compat newlocale_wrapper(int mask, const char* locale, locale_t_compat base);
void freelocale_wrapper(locale_t_compat loc);
locale_t_compat duplocale_wrapper(locale_t_compat loc);
locale_t_compat uselocale_wrapper(locale_t_compat loc);

double strtod_l_wrapper(const char* str, char** endptr, locale_t_compat loc);
float strtof_l_wrapper(const char* str, char** endptr, locale_t_compat loc);
long double strtold_l_wrapper(const char* str, char** endptr, locale_t_compat loc);

int strcoll_l_wrapper(const char* s1, const char* s2, locale_t_compat loc);
size_t strxfrm_l_wrapper(char* dest, const char* src, size_t n, locale_t_compat loc);
int wcscoll_l_wrapper(const wchar_t* s1, const wchar_t* s2, locale_t_compat loc);
size_t wcsxfrm_l_wrapper(wchar_t* dest, const wchar_t* src, size_t n, locale_t_compat loc);

/* ctype _l functions */
int isalpha_l_wrapper(int c, locale_t_compat loc);
int isdigit_l_wrapper(int c, locale_t_compat loc);
int isalnum_l_wrapper(int c, locale_t_compat loc);
int isspace_l_wrapper(int c, locale_t_compat loc);
int isupper_l_wrapper(int c, locale_t_compat loc);
int islower_l_wrapper(int c, locale_t_compat loc);
int isprint_l_wrapper(int c, locale_t_compat loc);
int ispunct_l_wrapper(int c, locale_t_compat loc);
int isgraph_l_wrapper(int c, locale_t_compat loc);
int iscntrl_l_wrapper(int c, locale_t_compat loc);
int isxdigit_l_wrapper(int c, locale_t_compat loc);
int isblank_l_wrapper(int c, locale_t_compat loc);
int tolower_l_wrapper(int c, locale_t_compat loc);
int toupper_l_wrapper(int c, locale_t_compat loc);

/* wctype _l functions */
wint_t towlower_l_wrapper(wint_t wc, locale_t_compat loc);
wint_t towupper_l_wrapper(wint_t wc, locale_t_compat loc);
wctype_t wctype_l_wrapper(const char* name, locale_t_compat loc);
int iswctype_l_wrapper(wint_t wc, wctype_t desc, locale_t_compat loc);
int iswalpha_l_wrapper(wint_t wc, locale_t_compat loc);
int iswdigit_l_wrapper(wint_t wc, locale_t_compat loc);
int iswspace_l_wrapper(wint_t wc, locale_t_compat loc);
int iswupper_l_wrapper(wint_t wc, locale_t_compat loc);
int iswlower_l_wrapper(wint_t wc, locale_t_compat loc);
int iswprint_l_wrapper(wint_t wc, locale_t_compat loc);

size_t strftime_l_wrapper(char* s, size_t max, const char* fmt, 
                          const struct tm* tm, locale_t_compat loc);
size_t wcsftime_l_wrapper(wchar_t* s, size_t max, const wchar_t* fmt, 
                          const struct tm* tm, locale_t_compat loc);

char* nl_langinfo_l_wrapper(int item, locale_t_compat loc);
char* nl_langinfo_wrapper(int item);

/* string functions - glibc internal names */
char* strdup_wrapper(const char* s);

/* strerror with locale */
char* strerror_wrapper(int errnum);
char* strerror_l_wrapper(int errnum, locale_t_compat loc);
char* strerror_r_wrapper(int errnum, char* buf, size_t buflen);
char* __xpg_strerror_r_wrapper(int errnum, char* buf, size_t buflen);

/* sysconf wrapper */
long sysconf_wrapper(int name);

/* getsid wrapper */
pid_t getsid_wrapper(pid_t pid);

/* exit and atexit wrappers */
void exit_wrapper(int status);
int atexit_wrapper(void (*function)(void));
int __cxa_atexit_wrapper(void (*func)(void*), void* arg, void* dso_handle);
int __cxa_thread_atexit_wrapper(void (*func)(void*), void* arg, void* dso_handle);
int __cxa_thread_atexit_impl_wrapper(void (*func)(void*), void* arg, void* dso_handle);
void __cxa_finalize_wrapper(void* dso_handle);

/* qsort/bsearch with callback support */
void qsort_wrapper(void* base, size_t nmemb, size_t size, 
                   int (*compar)(const void*, const void*));
void* bsearch_wrapper(const void* key, const void* base, size_t nmemb,
                      size_t size, int (*compar)(const void*, const void*));
void* lfind_wrapper(const void* key, const void* base, size_t* nmemb,
                    size_t size, int (*compar)(const void*, const void*));
void* lsearch_wrapper(const void* key, void* base, size_t* nmemb,
                      size_t size, int (*compar)(const void*, const void*));

/* Binary tree wrappers (tsearch family) */
void* tsearch_wrapper(const void* key, void** rootp,
                      int (*compar)(const void*, const void*));
void* tfind_wrapper(const void* key, void* const* rootp,
                    int (*compar)(const void*, const void*));
void* tdelete_wrapper(const void* key, void** rootp,
                      int (*compar)(const void*, const void*));
void twalk_wrapper(const void* root,
                   void (*action)(const void* nodep, VISIT which, int depth));
void tdestroy_wrapper(void* root, void (*free_node)(void* nodep));

/* dlopen wrapper - can load glibc .so files */
void* dlopen_wrapper(const char* filename, int flags);

/* dlclose wrapper - prevents bionic from unloading glibc-bridge-loaded libraries */
int dlclose_wrapper(void* handle);

/* dlsym wrapper - checks glibc-bridge symbol table first for glibc compatibility */
void* dlsym_wrapper(void* handle, const char* symbol);

/* dladdr wrapper - looks up glibc-bridge-loaded libraries first */
int dladdr_wrapper(const void* addr, Dl_info* info);

/* strtol/strtoul with locale */
long strtol_l_wrapper(const char* str, char** endptr, int base, locale_t_compat loc);
long long strtoll_l_wrapper(const char* str, char** endptr, int base, locale_t_compat loc);
unsigned long strtoul_l_wrapper(const char* str, char** endptr, int base, locale_t_compat loc);
unsigned long long strtoull_l_wrapper(const char* str, char** endptr, int base, locale_t_compat loc);

/* vsnprintf/snprintf wrappers - fix .NET hostpolicy MTE pointer truncation */
int vsnprintf_wrapper(char* str, size_t size, const char* format, va_list ap);
int snprintf_wrapper(char* str, size_t size, const char* format, ...);

/* C2x/C23 _Float64 functions */
double strtof64_wrapper(const char *nptr, char **endptr);
int strfromf64_wrapper(char *str, size_t n, const char *format, double fp);

/* strtoull wrappers */
unsigned long long strtoull_wrapper(const char* nptr, char** endptr, int base);
unsigned long long isoc23_strtoull_wrapper(const char* nptr, char** endptr, int base);

/* ============================================================================
 * FORTIFY Wrappers (wrapper_fortify.c)
 * ============================================================================ */

wchar_t* wmemset_chk_wrapper(wchar_t* s, wchar_t c, size_t n, size_t destlen);
wchar_t* wmemcpy_chk_wrapper(wchar_t* dest, const wchar_t* src, 
                              size_t n, size_t destlen);
wchar_t* wmemmove_chk_wrapper(wchar_t* dest, const wchar_t* src, 
                               size_t n, size_t destlen);
size_t mbsnrtowcs_chk_wrapper(wchar_t* dest, const char** src, 
                               size_t nms, size_t len, mbstate_t* ps, size_t destlen);
size_t mbsrtowcs_chk_wrapper(wchar_t* dest, const char** src, 
                              size_t len, mbstate_t* ps, size_t destlen);
int fprintf_chk_wrapper(FILE* stream, int flag, const char* fmt, ...);
int sprintf_chk_wrapper(char* str, int flag, size_t strlen, const char* fmt, ...);
int snprintf_chk_wrapper(char* str, size_t maxlen, int flag, 
                         size_t strlen, const char* fmt, ...);

/* ============================================================================
 * gettext Wrappers (wrapper_gettext.c)
 * ============================================================================ */

char* gettext_wrapper(const char* msgid);
char* dgettext_wrapper(const char* domainname, const char* msgid);
char* dcgettext_wrapper(const char* domainname, const char* msgid, int category);
char* ngettext_wrapper(const char* msgid1, const char* msgid2, unsigned long n);
char* bindtextdomain_wrapper(const char* domainname, const char* dirname);
char* bind_textdomain_codeset_wrapper(const char* domainname, const char* codeset);
char* textdomain_wrapper(const char* domainname);

/* ============================================================================
 * C++ Wrappers (wrapper_cxx.c)
 * ============================================================================ */

void ios_base_Init_ctor_wrapper(void* this_ptr);
void ios_base_Init_dtor_wrapper(void* this_ptr);
void terminate_wrapper(void);

void throw_logic_error_wrapper(const char* what);
void throw_length_error_wrapper(const char* what);
void throw_out_of_range_wrapper(const char* what);
void throw_out_of_range_fmt_wrapper(const char* fmt, ...);
void throw_invalid_argument_wrapper(const char* what);
void throw_bad_cast_wrapper(void);

/* ============================================================================
 * Symbol Table (glibc_bridge_symbol_table.c)
 * ============================================================================ */

/* Get the global symbol wrapper table */
const symbol_wrapper_t* glibc_bridge_get_symbol_table(void);

/* Get pointer to __libc_single_threaded variable */
char* glibc_bridge_get_libc_single_threaded(void);

/* ============================================================================
 * App Files Directory
 * ============================================================================ */

void glibc_bridge_set_app_files_dir(const char* dir);
const char* glibc_bridge_get_app_base_dir(void);

/* ============================================================================
 * Global Program Name Variables (for glibc compatibility)
 * ============================================================================ */

extern char* __progname;
/* ============================================================================
 * Socket wrapper
 * ============================================================================ */
int socket_wrapper(int domain, int type, int protocol);

/* ============================================================================
 * Signal wrappers
 * ============================================================================ */
void* signal_wrapper(int signum, void* handler);
int raise_wrapper(int sig);

/* ============================================================================
 * Process info wrappers
 * ============================================================================ */
pid_t getsid_wrapper(pid_t pid);

/* ============================================================================
 * dlsym wrapper
 * ============================================================================ */
void* dlsym_wrapper(void* handle, const char* symbol);

/* ============================================================================
 * FORTIFY printf extensions (wrapper_fortify.c)
 * ============================================================================ */
int printf_chk_wrapper(int flag, const char* fmt, ...);
int vprintf_chk_wrapper(int flag, const char* fmt, va_list ap);
int vfprintf_chk_wrapper(FILE* stream, int flag, const char* fmt, va_list ap);
int vsprintf_chk_wrapper(char* str, int flag, size_t strlen, const char* fmt, va_list ap);
int vsnprintf_chk_wrapper(char* str, size_t maxlen, int flag, size_t strlen, const char* fmt, va_list ap);
int vdprintf_chk_wrapper(int fd, int flag, const char* fmt, va_list ap);
int vfwprintf_chk_wrapper(FILE* stream, int flag, const wchar_t* fmt, va_list ap);
void vsyslog_chk_wrapper(int priority, int flag, const char* fmt, va_list ap);
void syslog_chk_wrapper(int priority, int flag, const char* fmt, ...);
long fdelt_chk_wrapper(long fd);
int open64_2_wrapper(const char* path, int flags);

/* ============================================================================
 * Math extensions (wrapper_math_ext.c)
 * ============================================================================ */
double exp10_wrapper(double x);
float exp10f_wrapper(float x);
long double exp10l_wrapper(long double x);
double pow10_wrapper(double x);
float pow10f_wrapper(float x);
long double pow10l_wrapper(long double x);

/* Complex math wrappers - ensure proper ABI handling */
double cabs_wrapper(double real, double imag);
double carg_wrapper(double real, double imag);
float cabsf_wrapper(float real, float imag);
float cargf_wrapper(float real, float imag);
double creal_wrapper(double real, double imag);
double cimag_wrapper(double real, double imag);

/* ============================================================================
 * ucontext (wrapper_ucontext.c) - stubs
 * ============================================================================ */
int getcontext_wrapper(void* ucp);
int setcontext_wrapper(const void* ucp);
int swapcontext_wrapper(void* oucp, const void* ucp);
void makecontext_wrapper(void* ucp, void (*func)(void), int argc, ...);
int sigsetjmp_wrapper(sigjmp_buf env, int savemask);

/* ============================================================================
 * pthread extensions (wrapper_pthread_ext.c)
 * ============================================================================ */
int pthread_setattr_default_np_wrapper(const pthread_attr_t* attr);
int pthread_getattr_default_np_wrapper(pthread_attr_t* attr);
int pthread_attr_setaffinity_np_wrapper(pthread_attr_t* attr, size_t cpusetsize, const cpu_set_t* cpuset);
int pthread_attr_getaffinity_np_wrapper(const pthread_attr_t* attr, size_t cpusetsize, cpu_set_t* cpuset);
void pthread_cleanup_push_wrapper(void* routine, void* arg);
void pthread_cleanup_pop_wrapper(int execute);

/* ============================================================================
 * obstack (wrapper_obstack.c)
 * NOTE: obstack_alloc_failed_handler is exported as a DATA symbol directly,
 * not through a getter function. See glibc_bridge_symbol_table.c.
 * ============================================================================ */
void** get_obstack_alloc_failed_handler_ptr(void);
int obstack_begin_wrapper(void* h, size_t size, size_t alignment, void* (*chunkfun)(size_t), void (*freefun)(void*));
int obstack_begin_1_wrapper(void* h, size_t size, size_t alignment, void* (*chunkfun)(void*, size_t), void (*freefun)(void*, void*), void* arg);
void obstack_free_wrapper(void* h, void* obj);
int obstack_vprintf_wrapper(void* obstack, const char* format, va_list ap);
int obstack_printf_wrapper(void* obstack, const char* format, ...);
int obstack_vprintf_chk_wrapper(void* obstack, int flag, const char* format, va_list ap);

/* ============================================================================
 * sysinfo (wrapper_sysinfo.c)
 * ============================================================================ */
long sysconf_internal_wrapper(int name);
int getcpu_wrapper(unsigned* cpu, unsigned* node);
int malloc_trim_wrapper(size_t pad);
void* libc_malloc_wrapper(size_t size);
void* libc_calloc_wrapper(size_t nmemb, size_t size);
void* libc_realloc_wrapper(void* ptr, size_t size);
void libc_free_wrapper(void* ptr);
int shm_unlink_wrapper(const char* name);
int dlinfo_wrapper(void* handle, int request, void* info);
void* fts64_open_wrapper(char* const* path_argv, int options, int (*compar)(const void**, const void**));
void* fts64_read_wrapper(void* ftsp);
int fts64_close_wrapper(void* ftsp);
void globfree64_wrapper(void* pglob);
int getprotobyname_r_wrapper(const char* name, void* result_buf, char* buf, size_t buflen, void** result);
int isoc99_vwscanf_wrapper(const void* format, void* ap);
int isoc99_vswscanf_wrapper(const void* s, const void* format, void* ap);
int isoc99_vfwscanf_wrapper(void* stream, const void* format, void* ap);
int shm_open_wrapper(const char* name, int oflag, mode_t mode);
void* libc_memalign_wrapper(size_t alignment, size_t size);
unsigned long getauxval_internal_wrapper(unsigned long type);
void* res_state_wrapper(void);
int getprotobynumber_r_wrapper(int proto, void* result_buf, char* buf, size_t buflen, void** result);
int glob64_wrapper(const char* pattern, int flags, int (*errfunc)(const char*, int), void* pglob);

/* ============================================================================
 * FORTIFY additions (wrapper_fortify.c)
 * ============================================================================ */
int vasprintf_chk_wrapper(char** strp, int flag, const char* fmt, va_list ap);
int vswprintf_chk_wrapper(wchar_t* s, size_t maxlen, int flag, size_t slen, const wchar_t* fmt, va_list ap);
int vwprintf_chk_wrapper(int flag, const wchar_t* fmt, va_list ap);
void longjmp_chk_wrapper(jmp_buf env, int val);
void chk_fail_wrapper(void);
void __stack_chk_fail_wrapper(void);
void abort_wrapper(void) __attribute__((noreturn));
void* glibc_bridge_get_stack_chk_guard(void);  /* Returns &__stack_chk_guard */

/* FORTIFY_SOURCE wrappers (glibc security hardening functions) */
void __explicit_bzero_chk_wrapper(void* dest, size_t len, size_t destlen);
size_t __mbstowcs_chk_wrapper(wchar_t* dest, const char* src, size_t n, size_t destlen);
size_t __wcstombs_chk_wrapper(char* dest, const wchar_t* src, size_t n, size_t destlen);
void* __memcpy_chk_wrapper(void* dest, const void* src, size_t n, size_t destlen);
void* __memmove_chk_wrapper(void* dest, const void* src, size_t n, size_t destlen);
void* __memset_chk_wrapper(void* dest, int c, size_t n, size_t destlen);
char* __strcpy_chk_wrapper(char* dest, const char* src, size_t destlen);
char* __strncpy_chk_wrapper(char* dest, const char* src, size_t n, size_t destlen);
char* __strcat_chk_wrapper(char* dest, const char* src, size_t destlen);
char* __strncat_chk_wrapper(char* dest, const char* src, size_t n, size_t destlen);
ssize_t __readlinkat_chk_wrapper(int dirfd, const char* pathname, char* buf, size_t bufsiz, size_t buflen);
int __openat64_2_wrapper(int dirfd, const char* pathname, int flags);

/* glibc-specific functions (not in bionic) */
size_t parse_printf_format_wrapper(const char* fmt, size_t n, int* argtypes);
const char* strerrorname_np_wrapper(int errnum);
const char* strerrordesc_np_wrapper(int errnum);
char* get_current_dir_name_wrapper(void);
int getdtablesize_wrapper(void);
int sigisemptyset_wrapper(const sigset_t* set);

/* Linux-specific syscall wrappers */
int open_tree_wrapper(int dirfd, const char* pathname, unsigned int flags);
int pidfd_open_wrapper(pid_t pid, unsigned int flags);
int pidfd_send_signal_wrapper(int pidfd, int sig, siginfo_t* info, unsigned int flags);
int name_to_handle_at_wrapper(int dirfd, const char* pathname, void* handle, int* mount_id, int flags);

/* ============================================================================
 * pthread mutex extensions (wrapper_pthread_ext.c)
 * ============================================================================ */
int pthread_mutexattr_setrobust_wrapper(pthread_mutexattr_t* attr, int robustness);
int pthread_mutexattr_getrobust_wrapper(const pthread_mutexattr_t* attr, int* robustness);
int pthread_mutexattr_setprioceiling_wrapper(pthread_mutexattr_t* attr, int prioceiling);
int pthread_mutexattr_getprioceiling_wrapper(const pthread_mutexattr_t* attr, int* prioceiling);
int pthread_mutex_consistent_wrapper(pthread_mutex_t* mutex);

/* pthread cancellation stubs (not supported on Android) */
int pthread_setcancelstate_wrapper(int state, int* oldstate);
int pthread_setcanceltype_wrapper(int type, int* oldtype);
void pthread_testcancel_wrapper(void);
int pthread_cancel_wrapper(pthread_t thread);

void pthread_register_cancel_wrapper(void* buf);
void pthread_unregister_cancel_wrapper(void* buf);
void pthread_unwind_next_wrapper(void* buf);

/* ============================================================================
 * obstack additions (wrapper_obstack.c)
 * ============================================================================ */
void obstack_free_direct_wrapper(void* h, void* obj);
void obstack_newchunk_wrapper(void* h, size_t length);

/* ============================================================================
 * stdio extensions (wrapper_stdio_ext.c)
 * ============================================================================ */
/* glibc cookie_io_functions_t structure */
typedef struct {
    ssize_t (*read)(void*, char*, size_t);
    ssize_t (*write)(void*, const char*, size_t);
    int (*seek)(void*, off64_t*, int);
    int (*close)(void*);
} glibc_bridge_cookie_io_functions_t;
FILE* fopencookie_wrapper(void* cookie, const char* mode, glibc_bridge_cookie_io_functions_t io_funcs);

/* ============================================================================
 * Time and Clock wrappers
 * ============================================================================ */
#include <time.h>
int clock_gettime_wrapper(clockid_t clk_id, struct timespec *tp);
int nanosleep_wrapper(const struct timespec *req, struct timespec *rem);

/* select/pselect wrappers - handle sigset_t conversion */
#include <sys/select.h>
int select_wrapper(int nfds, fd_set *readfds, fd_set *writefds,
                   fd_set *exceptfds, struct timeval *timeout);
int pselect_wrapper(int nfds, fd_set *readfds, fd_set *writefds,
                    fd_set *exceptfds, const struct timespec *timeout,
                    const sigset_t *sigmask);

/* div/ldiv/lldiv use direct pass-through to bionic */

/* ============================================================================
 * Character classification wrappers
 * ============================================================================ */
int isgraph_wrapper(int c);

/* __errno_location_wrapper is declared earlier in this file */

/* ============================================================================
 * Network wrappers
 * ============================================================================ */
#include <netdb.h>
int getaddrinfo_wrapper(const char *node, const char *service,
                        const struct addrinfo *hints, struct addrinfo **res);
int inet_pton_wrapper(int af, const char *src, void *dst);

/* ============================================================================
 * Wide character wrappers
 * ============================================================================ */
#include <wchar.h>
wchar_t* wcschr_wrapper(const wchar_t *wcs, wchar_t wc);
wchar_t* wcsrchr_wrapper(const wchar_t *wcs, wchar_t wc);
wchar_t* wcspbrk_wrapper(const wchar_t *wcs, const wchar_t *accept);
wchar_t* wmemcpy_wrapper(wchar_t *dest, const wchar_t *src, size_t n);
wchar_t* wmemset_wrapper(wchar_t *wcs, wchar_t wc, size_t n);
double wcstod_wrapper(const wchar_t *nptr, wchar_t **endptr);

/* ============================================================================
 * Global variables
 * ============================================================================ */
extern char* __progname_full;
extern char* program_invocation_name;
extern char* program_invocation_short_name;

/* ============================================================================
 * glibc-specific functions not in bionic (wrapper_libc.c)
 * ============================================================================ */
void* rawmemchr_wrapper(const void* s, int c);
int __xmknod_wrapper(int ver, const char* path, mode_t mode, dev_t* dev);

/* crypt - password encryption */
struct crypt_data;  /* Forward declaration */
char* crypt_wrapper(const char* key, const char* salt);
char* crypt_r_wrapper(const char* key, const char* salt, struct crypt_data* data);

/* POSIX Message Queue wrappers (stub - Android bionic doesn't support mqueue) */
typedef int mqd_t;
struct mq_attr {
    long mq_flags;
    long mq_maxmsg;
    long mq_msgsize;
    long mq_curmsgs;
};
mqd_t mq_open_wrapper(const char* name, int oflag, ...);
int mq_close_wrapper(mqd_t mqdes);
int mq_unlink_wrapper(const char* name);
int mq_send_wrapper(mqd_t mqdes, const char* msg_ptr, size_t msg_len, unsigned int msg_prio);
ssize_t mq_receive_wrapper(mqd_t mqdes, char* msg_ptr, size_t msg_len, unsigned int* msg_prio);
int mq_getattr_wrapper(mqd_t mqdes, struct mq_attr* attr);
int mq_setattr_wrapper(mqd_t mqdes, const struct mq_attr* newattr, struct mq_attr* oldattr);

/* POSIX AIO (Asynchronous I/O) wrappers - stub implementation */
struct aiocb {
    int             aio_fildes;
    off_t           aio_offset;
    volatile void*  aio_buf;
    size_t          aio_nbytes;
    int             aio_reqprio;
    struct sigevent aio_sigevent;
    int             aio_lio_opcode;
    /* Internal fields */
    int             __error_code;
    ssize_t         __return_value;
};

int aio_read_wrapper(struct aiocb* aiocbp);
int aio_write_wrapper(struct aiocb* aiocbp);
int aio_error_wrapper(const struct aiocb* aiocbp);
ssize_t aio_return_wrapper(struct aiocb* aiocbp);
int aio_suspend_wrapper(const struct aiocb* const list[], int nent, const struct timespec* timeout);
int aio_cancel_wrapper(int fd, struct aiocb* aiocbp);
int aio_fsync_wrapper(int op, struct aiocb* aiocbp);
int lio_listio_wrapper(int mode, struct aiocb* const list[], int nent, struct sigevent* sig);

/* System V IPC wrappers - memory-based implementation to avoid seccomp */
#include <sys/types.h>

/* Shared memory */
int shmget_wrapper(key_t key, size_t size, int shmflg);
void* shmat_wrapper(int shmid, const void* shmaddr, int shmflg);
int shmdt_wrapper(const void* shmaddr);
int shmctl_wrapper(int shmid, int cmd, void* buf);

/* Semaphores */
int semget_wrapper(key_t key, int nsems, int semflg);
int semop_wrapper(int semid, void* sops, size_t nsops);
int semctl_wrapper(int semid, int semnum, int cmd, ...);

/* Message queues (System V style) */
int msgget_wrapper(key_t key, int msgflg);
int msgsnd_wrapper(int msqid, const void* msgp, size_t msgsz, int msgflg);
ssize_t msgrcv_wrapper(int msqid, void* msgp, size_t msgsz, long msgtyp, int msgflg);
int msgctl_wrapper(int msqid, int cmd, void* buf);

/* File creation wrappers */
int mkfifo_wrapper(const char* pathname, mode_t mode);
int mknod_wrapper(const char* pathname, mode_t mode, dev_t dev);
int mknodat_wrapper(int dirfd, const char* pathname, mode_t mode, dev_t dev);

/* Signal handling wrappers */
#include <signal.h>
int sigprocmask_wrapper(int how, const sigset_t* set, sigset_t* oldset);
int sigaction_wrapper(int signum, const struct sigaction* act, struct sigaction* oldact);
int sigemptyset_wrapper(sigset_t* set);
int sigfillset_wrapper(sigset_t* set);
int sigaddset_wrapper(sigset_t* set, int signum);
int sigdelset_wrapper(sigset_t* set, int signum);
int sigismember_wrapper(const sigset_t* set, int signum);
int kill_wrapper(pid_t pid, int sig);
int raise_wrapper(int sig);

/* System configuration (emulated for bionic) */
size_t confstr_wrapper(int name, char* buf, size_t len);

/* .NET CoreCLR PAL stub */
int PAL_RegisterModule_wrapper(const char* name);

/* Environment variable pointer (data symbol) */
void* glibc_bridge_get_environ_addr(void);

/* ============================================================================
 * FORTIFY_SOURCE checked functions (wrapper_fortify.c)
 * ============================================================================ */
/* Wide char memory */
wchar_t* wmemset_chk_wrapper(wchar_t* s, wchar_t c, size_t n, size_t destlen);
wchar_t* wmemcpy_chk_wrapper(wchar_t* dest, const wchar_t* src, size_t n, size_t destlen);
wchar_t* wmemmove_chk_wrapper(wchar_t* dest, const wchar_t* src, size_t n, size_t destlen);

/* Multibyte conversion */
size_t mbsnrtowcs_chk_wrapper(wchar_t* dest, const char** src, size_t nms, size_t len, mbstate_t* ps, size_t destlen);
size_t mbsrtowcs_chk_wrapper(wchar_t* dest, const char** src, size_t len, mbstate_t* ps, size_t destlen);

/* printf family */
int fprintf_chk_wrapper(FILE* stream, int flag, const char* fmt, ...);
int sprintf_chk_wrapper(char* str, int flag, size_t strlen, const char* fmt, ...);
int snprintf_chk_wrapper(char* str, size_t maxlen, int flag, size_t strlen, const char* fmt, ...);
int printf_chk_wrapper(int flag, const char* fmt, ...);
int vprintf_chk_wrapper(int flag, const char* fmt, va_list ap);
int vfprintf_chk_wrapper(FILE* stream, int flag, const char* fmt, va_list ap);
int vsprintf_chk_wrapper(char* str, int flag, size_t strlen, const char* fmt, va_list ap);
int vsnprintf_chk_wrapper(char* str, size_t maxlen, int flag, size_t strlen, const char* fmt, va_list ap);
int vdprintf_chk_wrapper(int fd, int flag, const char* fmt, va_list ap);
int vfwprintf_chk_wrapper(FILE* stream, int flag, const wchar_t* fmt, va_list ap);
void vsyslog_chk_wrapper(int priority, int flag, const char* fmt, va_list ap);
void syslog_chk_wrapper(int priority, int flag, const char* fmt, ...);
long fdelt_chk_wrapper(long fd);
int open64_2_wrapper(const char* path, int flags);
int vasprintf_chk_wrapper(char** strp, int flag, const char* fmt, va_list ap);
int vswprintf_chk_wrapper(wchar_t* s, size_t maxlen, int flag, size_t slen, const wchar_t* fmt, va_list ap);
int vwprintf_chk_wrapper(int flag, const wchar_t* fmt, va_list ap);
void longjmp_chk_wrapper(jmp_buf env, int val);
void chk_fail_wrapper(void);

/* Wide string fortify */
int swprintf_chk_wrapper(wchar_t* s, size_t maxlen, int flag, size_t slen, const wchar_t* fmt, ...);
wchar_t* wcscat_chk_wrapper(wchar_t* dest, const wchar_t* src, size_t destlen);
wchar_t* wcscpy_chk_wrapper(wchar_t* dest, const wchar_t* src, size_t destlen);
wchar_t* wcsncat_chk_wrapper(wchar_t* dest, const wchar_t* src, size_t n, size_t destlen);
wchar_t* wcsncpy_chk_wrapper(wchar_t* dest, const wchar_t* src, size_t n, size_t destlen);

/* String fortify */
int asprintf_chk_wrapper(char** strp, int flag, const char* fmt, ...);
char* realpath_chk_wrapper(const char* path, char* resolved_path, size_t resolved_len);
char* stpcpy_chk_wrapper(char* dest, const char* src, size_t destlen);
char* stpncpy_chk_wrapper(char* dest, const char* src, size_t n, size_t destlen);
char* strcat_chk_wrapper(char* dest, const char* src, size_t destlen);
char* strcpy_chk_wrapper(char* dest, const char* src, size_t destlen);
char* strncat_chk_wrapper(char* dest, const char* src, size_t n, size_t destlen);
char* strncpy_chk_wrapper(char* dest, const char* src, size_t n, size_t destlen);

/* Memory fortify */
void* memcpy_chk_wrapper(void* dest, const void* src, size_t n, size_t destlen);
void* memmove_chk_wrapper(void* dest, const void* src, size_t n, size_t destlen);
void* memset_chk_wrapper(void* s, int c, size_t n, size_t destlen);

/* ============================================================================
 * PRoot bypass wrappers - Android compatibility
 * ============================================================================ */

/* iconv - character set conversion */
void* iconv_open_wrapper(const char* tocode, const char* fromcode);
size_t iconv_wrapper(void* cd, char** inbuf, size_t* inbytesleft,
                     char** outbuf, size_t* outbytesleft);
int iconv_close_wrapper(void* cd);

/* Socket options */
int setsockopt_wrapper(int sockfd, int level, int optname,
                       const void *optval, socklen_t optlen);
int getsockopt_wrapper(int sockfd, int level, int optname,
                       void *optval, socklen_t *optlen);

/* getopt */
int getopt_wrapper(int argc, char* const argv[], const char* optstring);

#ifdef __cplusplus
}
#endif

#endif /* GLIBC_BRIDGE_WRAPPERS_H */

