//
// Dynamic library loader utility
//
#include <string.h>
#include <stdio.h>
#include <dlfcn.h>
#include "loader_dlopen.h"

void* loader_dlopen(const char* primaryName, const char* secondaryName, int flags) {
    void* dl_handle;
    
    if (primaryName != NULL) {
        dl_handle = dlopen(primaryName, flags);
        if (dl_handle != NULL) {
            return dl_handle;
        }
    }
    
    if (secondaryName != NULL) {
        dl_handle = dlopen(secondaryName, flags);
        if (dl_handle != NULL) {
            return dl_handle;
        }
    }
    
    // Print error if both failed
    const char* error = dlerror();
    if (error != NULL) {
        printf("dlopen error: %s\n", error);
    }
    
    return NULL;
}

