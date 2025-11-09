#include "netcorehost/nethost.hpp"
#include "netcorehost/hostfxr.hpp"
#include <vector>
#include <cstring>

#ifdef _WINDOWS
    #include <windows.h>
    #define LOAD_LIBRARY(path) LoadLibraryW(path)
    #define GET_PROC_ADDRESS GetProcAddress
    #define CLOSE_LIBRARY FreeLibrary
    typedef HMODULE LibraryHandle;
#else
    #include <dlfcn.h>
    #define LOAD_LIBRARY(path) dlopen(path, RTLD_LAZY)
    #define GET_PROC_ADDRESS dlsym
    #define CLOSE_LIBRARY dlclose
    typedef void* LibraryHandle;
#endif

namespace netcorehost {

// 直接返回静态链接的 get_hostfxr_path 函数
// nethost 已经静态链接到我们的库中，不需要动态加载
bindings::get_hostfxr_path_fn Nethost::load_nethost_function() {
    // 直接返回静态链接的函数指针
    return &bindings::get_hostfxr_path;
}

PdCString Nethost::get_hostfxr_path_internal(const bindings::get_hostfxr_parameters* parameters) {
    auto get_hostfxr_path_func = load_nethost_function();
    
    // 首先尝试使用固定大小的缓冲区
    std::vector<bindings::char_t> buffer(bindings::MAX_PATH);
    size_t buffer_size = buffer.size();
    
    int32_t result = get_hostfxr_path_func(buffer.data(), &buffer_size, parameters);
    
    if (result == static_cast<int32_t>(bindings::StatusCode::HostApiBufferTooSmall)) {
        // 缓冲区太小，使用返回的所需大小重新分配
        buffer.resize(buffer_size);
        result = get_hostfxr_path_func(buffer.data(), &buffer_size, parameters);
    }
    
    auto hosting_result = HostingResult::from_status_code(result);
    hosting_result.throw_if_error();
    
    // 创建并返回字符串
#ifdef _WINDOWS
    return PdCString(std::wstring(buffer.data(), buffer_size - 1));
#else
    return PdCString(std::string(buffer.data(), buffer_size - 1));
#endif
}

PdCString Nethost::get_hostfxr_path() {
    return get_hostfxr_path_internal(nullptr);
}

PdCString Nethost::get_hostfxr_path_with_assembly_path(const PdCString& assembly_path) {
    auto params = bindings::get_hostfxr_parameters::with_assembly_path(assembly_path.c_str());
    return get_hostfxr_path_internal(&params);
}

PdCString Nethost::get_hostfxr_path_with_dotnet_root(const PdCString& dotnet_root) {
    auto params = bindings::get_hostfxr_parameters::with_dotnet_root(dotnet_root.c_str());
    return get_hostfxr_path_internal(&params);
}

std::shared_ptr<Hostfxr> Nethost::load_hostfxr() {
    auto hostfxr_path = get_hostfxr_path();
    return Hostfxr::load_from_path(hostfxr_path);
}

std::shared_ptr<Hostfxr> Nethost::load_hostfxr_with_assembly_path(const PdCString& assembly_path) {
    auto hostfxr_path = get_hostfxr_path_with_assembly_path(assembly_path);
    return Hostfxr::load_from_path(hostfxr_path);
}

std::shared_ptr<Hostfxr> Nethost::load_hostfxr_with_dotnet_root(const PdCString& dotnet_root) {
    auto hostfxr_path = get_hostfxr_path_with_dotnet_root(dotnet_root);
    return Hostfxr::load_from_path(hostfxr_path);
}

} // namespace netcorehost

