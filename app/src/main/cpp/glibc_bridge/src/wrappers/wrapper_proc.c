/*
 * glibc-bridge - /proc filesystem virtualization
 * 
 * Virtualizes /proc/self/maps to include glibc-bridge-loaded glibc libraries.
 * This allows .NET's Process.Modules to correctly enumerate all loaded modules.
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/mman.h>
#include <sys/syscall.h>

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_DEBUG(...) __android_log_print(ANDROID_LOG_DEBUG, "GLIBC_BRIDGE_PROC", __VA_ARGS__)
#define LOG_INFO(...) __android_log_print(ANDROID_LOG_INFO, "GLIBC_BRIDGE_PROC", __VA_ARGS__)
#else
#define LOG_DEBUG(...) fprintf(stderr, "[GLIBC_BRIDGE_PROC] " __VA_ARGS__)
#define LOG_INFO(...) fprintf(stderr, "[GLIBC_BRIDGE_PROC] " __VA_ARGS__)
#endif

/* memfd_create flags */
#ifndef MFD_CLOEXEC
#define MFD_CLOEXEC 0x0001U
#endif
#ifndef MFD_ALLOW_SEALING
#define MFD_ALLOW_SEALING 0x0002U
#endif

/* memfd_create syscall wrapper for Android */
static int memfd_create_wrapper(const char* name, unsigned int flags) {
#ifdef __NR_memfd_create
    return syscall(__NR_memfd_create, name, flags);
#else
    errno = ENOSYS;
    return -1;
#endif
}

/* External: get shared lib info from glibc_bridge_sharedlib.c */
typedef struct {
    const char* name;
    const char* path;
    void* base;
    size_t size;
} glibc_bridge_shlib_info_t;

extern int glibc_bridge_get_shared_lib_count(void);
extern int glibc_bridge_get_shared_lib_info(int index, glibc_bridge_shlib_info_t* info);

/* Virtual /proc/self/maps file handle tracking */
#define MAX_VIRTUAL_MAPS 8
typedef struct {
    int in_use;
    char* buffer;       /* Virtual maps content */
    size_t buffer_size;
    size_t read_pos;    /* Current read position */
} virtual_maps_t;

static virtual_maps_t g_virtual_maps[MAX_VIRTUAL_MAPS];

/* Permission string helper */
static const char* get_perm_string(int prot) {
    static char perm[5];
    perm[0] = (prot & PROT_READ) ? 'r' : '-';
    perm[1] = (prot & PROT_WRITE) ? 'w' : '-';
    perm[2] = (prot & PROT_EXEC) ? 'x' : '-';
    perm[3] = 'p';  /* Private mapping */
    perm[4] = '\0';
    return perm;
}

/* Build virtual /proc/self/maps content */
static char* build_virtual_maps(size_t* out_size) {
    /* First, read the real /proc/self/maps using fd-based reading
     * Note: /proc files are virtual and don't support fseek/ftell */
    int fd = open("/proc/self/maps", O_RDONLY);
    if (fd < 0) {
        LOG_DEBUG("build_virtual_maps: failed to open /proc/self/maps");
        return NULL;
    }
    
    /* Allocate initial buffer - /proc/self/maps can be large */
    int lib_count = glibc_bridge_get_shared_lib_count();
    size_t buffer_size = 64 * 1024;  /* Start with 64KB */
    size_t extra_size = lib_count * 256;  /* ~256 bytes per lib entry */
    
    char* buffer = (char*)malloc(buffer_size + extra_size);
    if (!buffer) {
        close(fd);
        return NULL;
    }
    
    /* Read real maps content in chunks */
    size_t total_read = 0;
    ssize_t n;
    while ((n = read(fd, buffer + total_read, buffer_size - total_read - 1)) > 0) {
        total_read += n;
        if (total_read >= buffer_size - 1024) {
            /* Need more space */
            buffer_size *= 2;
            char* new_buffer = (char*)realloc(buffer, buffer_size + extra_size);
            if (!new_buffer) {
                free(buffer);
                close(fd);
                return NULL;
            }
            buffer = new_buffer;
        }
    }
    close(fd);
    
    if (total_read == 0) {
        LOG_DEBUG("build_virtual_maps: failed to read /proc/self/maps");
        free(buffer);
        return NULL;
    }
    
    buffer[total_read] = '\0';
    size_t read_size = total_read;
    
    LOG_DEBUG("build_virtual_maps: read %zu bytes from /proc/self/maps", read_size);
    
    /* Now append glibc-bridge-loaded libraries */
    char* write_pos = buffer + read_size;
    size_t remaining = buffer_size + extra_size - read_size;
    
    for (int i = 0; i < lib_count; i++) {
        glibc_bridge_shlib_info_t info;
        if (glibc_bridge_get_shared_lib_info(i, &info) != 0) {
            continue;
        }
        
        if (!info.base || !info.path) {
            continue;
        }
        
        /* Check if this library is already in the maps (bionic-loaded) */
        if (strstr(buffer, info.path)) {
            continue;  /* Already present */
        }
        
        /* Add entry for this library
         * Format: start-end perms offset dev inode pathname
         * Example: 7f1234000000-7f1234100000 r-xp 00000000 00:00 0 /path/to/lib.so
         */
        uintptr_t start = (uintptr_t)info.base;
        uintptr_t end = start + info.size;
        
        int written = snprintf(write_pos, remaining,
            "%lx-%lx r-xp 00000000 00:00 0                          %s\n",
            (unsigned long)start, (unsigned long)end, info.path);
        
        if (written > 0 && (size_t)written < remaining) {
            write_pos += written;
            remaining -= written;
        }
    }
    
    *out_size = write_pos - buffer;
    return buffer;
}

/* Allocate a virtual maps handle */
static int alloc_virtual_maps(void) {
    for (int i = 0; i < MAX_VIRTUAL_MAPS; i++) {
        if (!g_virtual_maps[i].in_use) {
            g_virtual_maps[i].in_use = 1;
            g_virtual_maps[i].buffer = NULL;
            g_virtual_maps[i].buffer_size = 0;
            g_virtual_maps[i].read_pos = 0;
            return i;
        }
    }
    return -1;
}

/* Free a virtual maps handle */
static void free_virtual_maps(int handle) {
    if (handle >= 0 && handle < MAX_VIRTUAL_MAPS) {
        if (g_virtual_maps[handle].buffer) {
            free(g_virtual_maps[handle].buffer);
        }
        memset(&g_virtual_maps[handle], 0, sizeof(virtual_maps_t));
    }
}

/* Check if path is /proc/self/maps or /proc/<pid>/maps */
int glibc_bridge_is_proc_maps(const char* path) {
    if (!path) return 0;
    
    if (strcmp(path, "/proc/self/maps") == 0) {
        LOG_DEBUG("Detected /proc/self/maps access");
        return 1;
    }
    
    /* Check /proc/<pid>/maps where pid is our pid */
    char our_maps[64];
    snprintf(our_maps, sizeof(our_maps), "/proc/%d/maps", getpid());
    if (strcmp(path, our_maps) == 0) {
        LOG_DEBUG("Detected /proc/%d/maps access", getpid());
        return 1;
    }
    
    return 0;
}

/* Open virtual /proc/self/maps - returns a pseudo-fd (negative) or -1 on error */
int glibc_bridge_open_proc_maps(void) {
    int handle = alloc_virtual_maps();
    if (handle < 0) {
        errno = EMFILE;
        return -1;
    }
    
    size_t size;
    char* buffer = build_virtual_maps(&size);
    if (!buffer) {
        free_virtual_maps(handle);
        errno = EIO;
        return -1;
    }
    
    g_virtual_maps[handle].buffer = buffer;
    g_virtual_maps[handle].buffer_size = size;
    g_virtual_maps[handle].read_pos = 0;
    
    /* Return a pseudo-fd: use negative numbers starting from -1000 */
    return -(1000 + handle);
}

/* Check if fd is a virtual maps fd */
int glibc_bridge_is_virtual_maps_fd(int fd) {
    if (fd >= -1000 && fd < -1000 + MAX_VIRTUAL_MAPS) {
        int handle = -(fd + 1000);
        return g_virtual_maps[handle].in_use;
    }
    return 0;
}

/* Read from virtual maps */
ssize_t glibc_bridge_read_virtual_maps(int fd, void* buf, size_t count) {
    if (!glibc_bridge_is_virtual_maps_fd(fd)) {
        errno = EBADF;
        return -1;
    }
    
    int handle = -(fd + 1000);
    virtual_maps_t* vm = &g_virtual_maps[handle];
    
    if (!vm->buffer) {
        errno = EIO;
        return -1;
    }
    
    size_t available = vm->buffer_size - vm->read_pos;
    size_t to_read = (count < available) ? count : available;
    
    if (to_read > 0) {
        memcpy(buf, vm->buffer + vm->read_pos, to_read);
        vm->read_pos += to_read;
    }
    
    return (ssize_t)to_read;
}

/* Close virtual maps */
int glibc_bridge_close_virtual_maps(int fd) {
    if (!glibc_bridge_is_virtual_maps_fd(fd)) {
        errno = EBADF;
        return -1;
    }
    
    int handle = -(fd + 1000);
    free_virtual_maps(handle);
    return 0;
}

/* lseek for virtual maps */
off_t glibc_bridge_lseek_virtual_maps(int fd, off_t offset, int whence) {
    if (!glibc_bridge_is_virtual_maps_fd(fd)) {
        errno = EBADF;
        return -1;
    }
    
    int handle = -(fd + 1000);
    virtual_maps_t* vm = &g_virtual_maps[handle];
    
    off_t new_pos;
    switch (whence) {
        case SEEK_SET:
            new_pos = offset;
            break;
        case SEEK_CUR:
            new_pos = vm->read_pos + offset;
            break;
        case SEEK_END:
            new_pos = vm->buffer_size + offset;
            break;
        default:
            errno = EINVAL;
            return -1;
    }
    
    if (new_pos < 0 || (size_t)new_pos > vm->buffer_size) {
        errno = EINVAL;
        return -1;
    }
    
    vm->read_pos = new_pos;
    return new_pos;
}

/* ============================================================================
 * FILE* based virtual maps (for fopen)
 * ============================================================================ */

typedef struct {
    int in_use;
    char* buffer;
    size_t buffer_size;
    size_t read_pos;
} virtual_maps_file_t;

static virtual_maps_file_t g_virtual_maps_files[MAX_VIRTUAL_MAPS];

/* Open virtual maps as FILE* */
FILE* glibc_bridge_fopen_proc_maps(void) {
    LOG_INFO("glibc_bridge_fopen_proc_maps called - opening real /proc/self/maps (virtualization disabled for stability)");
    
    /* Directly open the real /proc/self/maps file
     * Virtualization is disabled to avoid mutex/threading issues with Box64 */
    FILE* f = fopen("/proc/self/maps", "r");
    if (!f) {
        LOG_DEBUG("glibc_bridge_fopen_proc_maps: fopen failed, errno=%d", errno);
        return NULL;
    }
    
    LOG_INFO("glibc_bridge_fopen_proc_maps: returning real FILE* %p", (void*)f);
    return f;
}

/* ============================================================================
 * FD-based virtual maps (for open syscall)
 * Uses memfd_create to create a real file descriptor with virtual content
 * ============================================================================ */

int glibc_bridge_open_proc_maps_fd(void) {
    LOG_INFO("glibc_bridge_open_proc_maps_fd called - virtualizing /proc/self/maps via memfd");
    
    /* Build the virtual maps content */
    size_t size;
    char* buffer = build_virtual_maps(&size);
    if (!buffer) {
        LOG_DEBUG("glibc_bridge_open_proc_maps_fd: build_virtual_maps failed");
        errno = EIO;
        return -1;
    }
    
    LOG_INFO("glibc_bridge_open_proc_maps_fd: built virtual maps, size=%zu, libs=%d", 
             size, glibc_bridge_get_shared_lib_count());
    
    /* Create a memory-backed file descriptor */
    int fd = memfd_create_wrapper("proc_maps", MFD_CLOEXEC);
    if (fd < 0) {
        LOG_DEBUG("glibc_bridge_open_proc_maps_fd: memfd_create failed, errno=%d", errno);
        free(buffer);
        return -1;
    }
    
    /* Write the virtual maps content to the memfd */
    ssize_t written = write(fd, buffer, size);
    free(buffer);
    
    if (written < 0 || (size_t)written != size) {
        LOG_DEBUG("glibc_bridge_open_proc_maps_fd: write failed, written=%zd, size=%zu", written, size);
        close(fd);
        errno = EIO;
        return -1;
    }
    
    /* Seek back to the beginning so it can be read */
    if (lseek(fd, 0, SEEK_SET) < 0) {
        LOG_DEBUG("glibc_bridge_open_proc_maps_fd: lseek failed");
        close(fd);
        return -1;
    }
    
    LOG_INFO("glibc_bridge_open_proc_maps_fd: returning fd=%d", fd);
    return fd;
}

