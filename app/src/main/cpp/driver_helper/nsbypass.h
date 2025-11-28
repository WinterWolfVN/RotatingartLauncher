//
// Namespace bypass for loading Turnip Vulkan driver
// Based on PojavLauncher's implementation
//

#ifndef RALAUNCH_NSBYPASS_H
#define RALAUNCH_NSBYPASS_H

#include <stdbool.h>

bool linker_ns_load(const char* lib_search_path);
void* linker_ns_dlopen(const char* name, int flag);
void* linker_ns_dlopen_unique(const char* tmpdir, const char* name, int flag);

#endif //RALAUNCH_NSBYPASS_H

