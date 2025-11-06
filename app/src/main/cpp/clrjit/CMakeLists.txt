# CoreCLR JIT for Android ARM64
# 
# 此 CMake 配置将 CoreCLR JIT 编译为 libclrjit.so
# 用于替代 Mono JIT，以支持 IgnoresAccessChecksTo 特性

cmake_minimum_required(VERSION 3.22.1)
project(clrjit)

# 设置 C++ 标准
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# CoreCLR JIT 源码路径（需要用户提供）
set(CORECLR_ROOT "D:/runtime-release-8.0/src/coreclr" CACHE PATH "Path to CoreCLR source")
set(JIT_SRC_DIR "${CORECLR_ROOT}/jit")

# 检查源码路径是否存在
if(NOT EXISTS "${JIT_SRC_DIR}")
    message(FATAL_ERROR "CoreCLR JIT source not found at: ${JIT_SRC_DIR}")
endif()

# 定义平台宏
add_definitions(
    -DTARGET_UNIX
    -DTARGET_LINUX
    -DTARGET_ARM64
    -DTARGET_64BIT
    -DFEATURE_SIMD
    -DFEATURE_HW_INTRINSICS
    -DHOST_ARM64
    -DHOST_64BIT
    -D_TARGET_ARM64_=1
    -DCLR_CMAKE_TARGET_ARCH_ARM64
    -DBIT64
    -D__LINUX__
    -D__linux__
)

# 包含目录
include_directories(
    ${JIT_SRC_DIR}
    ${CORECLR_ROOT}/inc
    ${CORECLR_ROOT}/jit/unwind
    ${CORECLR_ROOT}/pal/inc
    ${CORECLR_ROOT}/pal/inc/rt
    ${CORECLR_ROOT}/pal/prebuilt/inc
    ${CORECLR_ROOT}/debug/inc
    ${CORECLR_ROOT}/md/inc
    ${CORECLR_ROOT}/classlibnative/inc
    ${CORECLR_ROOT}/vm
    ${CORECLR_ROOT}/gc
)

# 收集所有 JIT 源文件
file(GLOB JIT_SOURCES
    "${JIT_SRC_DIR}/*.cpp"
)

# 排除不需要的文件
list(FILTER JIT_SOURCES EXCLUDE REGEX ".*superpmi.*")
list(FILTER JIT_SOURCES EXCLUDE REGEX ".*jitpch.*")

# 添加共享库目标
add_library(clrjit SHARED ${JIT_SOURCES})

# 链接选项
target_link_libraries(clrjit
    log
    android
)

# 编译选项
target_compile_options(clrjit PRIVATE
    -fPIC
    -fno-strict-aliasing
    -fvisibility=hidden
    -Wno-unused-variable
    -Wno-unused-function
    -Wno-missing-field-initializers
)

# 导出符号
set_target_properties(clrjit PROPERTIES
    VERSION 8.0.0
    SOVERSION 8
    PUBLIC_HEADER ""
)

message(STATUS "CoreCLR JIT configuration:")
message(STATUS "  Source dir: ${JIT_SRC_DIR}")
message(STATUS "  Source files: ${JIT_SOURCES}")
message(STATUS "  Target: libclrjit.so for Android ARM64")

