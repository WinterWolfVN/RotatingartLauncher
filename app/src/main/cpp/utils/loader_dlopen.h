//
// Dynamic library loader utility
//

#ifndef LOADER_DLOPEN_H
#define LOADER_DLOPEN_H

#include <dlfcn.h>

/**
 * @brief Load a dynamic library with fallback support
 * @param primaryName Primary library name (e.g., "libOSMesa.so.8")
 * @param secondaryName Secondary library name (e.g., "libOSMesa.so")
 * @param flags dlopen flags (e.g., RTLD_LOCAL | RTLD_LAZY)
 * @return Library handle on success, NULL on failure
 */
void* loader_dlopen(const char* primaryName, const char* secondaryName, int flags);

#endif // LOADER_DLOPEN_H

