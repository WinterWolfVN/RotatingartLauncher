cmake_minimum_required(VERSION 3.10)
project(glibc_bridge VERSION 1.0.0 LANGUAGES C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# ============================================================================
# Build Options
# ============================================================================

option(GLIBC_BRIDGE_DEBUG "Enable debug features" OFF)

# ============================================================================
# Compiler Flags
# ============================================================================

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra -Wno-unused-parameter -fPIC")
set(CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS_DEBUG} -g -O0 -DDEBUG")
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O2 -DNDEBUG")

if(GLIBC_BRIDGE_DEBUG)
    add_definitions(-DGLIBC_BRIDGE_DEBUG=1)
endif()

# ============================================================================
# Include Directories
# ============================================================================

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/src/include
    ${CMAKE_CURRENT_SOURCE_DIR}/src/compat
    ${CMAKE_CURRENT_SOURCE_DIR}/src/dynlink
)

# ============================================================================
# Source Files - Core glibc-bridge
# ============================================================================

set(GLIBC_BRIDGE_CORE_SOURCES
    src/glibc_bridge_core.c
    src/glibc_bridge_loader.c
    src/glibc_bridge_runner.c
    src/glibc_bridge_dynlink.c
    src/glibc_bridge_stdio.c
    src/glibc_bridge_tls.c
    src/glibc_bridge_sharedlib.c
    src/glibc_bridge_fake_root.c
    src/glibc_bridge_error_hook.c
    src/glibc_bridge_jni.c
)

# Dynamic linking subsystem
set(GLIBC_BRIDGE_DYNLINK_SOURCES
    src/dynlink/glibc_bridge_log.c
    src/dynlink/glibc_bridge_reloc.c
    src/dynlink/glibc_bridge_resolver.c
    src/dynlink/glibc_bridge_symbol_table.c
)

# Wrapper functions (glibc->bionic)
set(GLIBC_BRIDGE_WRAPPER_SOURCES
    src/wrappers/wrapper_libc.c
    src/wrappers/wrapper_stat.c
    src/wrappers/wrapper_locale.c
    src/wrappers/wrapper_fortify.c
    src/wrappers/wrapper_gettext.c
    src/wrappers/wrapper_cxx.c
    src/wrappers/wrapper_stdio_ext.c
    src/wrappers/wrapper_pthread_ext.c
    src/wrappers/wrapper_math_ext.c
    src/wrappers/wrapper_sysinfo.c
    src/wrappers/wrapper_ucontext.c
    src/wrappers/wrapper_obstack.c
    src/wrappers/wrapper_mlock.c
    src/wrappers/wrapper_proc.c
    src/wrappers/wrapper_common.c
    src/wrappers/proot_bypass.c
)

# ============================================================================
# glibc-bridge shared library
# ============================================================================

add_library(glibc_bridge SHARED
    ${GLIBC_BRIDGE_CORE_SOURCES}
    ${GLIBC_BRIDGE_DYNLINK_SOURCES}
    ${GLIBC_BRIDGE_WRAPPER_SOURCES}
)

target_include_directories(glibc_bridge PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src/include
    ${CMAKE_CURRENT_SOURCE_DIR}/src/dynlink
    ${CMAKE_CURRENT_SOURCE_DIR}/src/wrappers
)

# Android specific
find_library(log-lib log)
find_library(dl-lib dl)

target_link_libraries(glibc_bridge
    ${log-lib}
    ${dl-lib}
    m
)

set_target_properties(glibc_bridge PROPERTIES
    VERSION ${PROJECT_VERSION}
    SOVERSION 1
    OUTPUT_NAME "glibc_bridge"
)

# ============================================================================
# Summary
# ============================================================================

message(STATUS "")
message(STATUS "========================================")
message(STATUS "glibc-bridge Configuration Summary")
message(STATUS "========================================")
message(STATUS "Version: ${PROJECT_VERSION}")
message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "C Compiler: ${CMAKE_C_COMPILER}")
message(STATUS "========================================")
message(STATUS "")
