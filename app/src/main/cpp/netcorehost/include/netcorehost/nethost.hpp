#ifndef NETCOREHOST_NETHOST_HPP
#define NETCOREHOST_NETHOST_HPP

#include <string>
#include <memory>
#include "bindings.hpp"
#include "pdcstring.hpp"
#include "error.hpp"

namespace netcorehost {

// 前向声明
class Hostfxr;

/**
 * Nethost API - 用于定位和加载 hostfxr 库
 */
class Nethost {
public:
    /**
     * 获取 hostfxr 库的路径
     */
    static PdCString get_hostfxr_path();
    
    /**
     * 获取 hostfxr 库的路径
     * 使用指定的程序集路径作为 apphost
     */
    static PdCString get_hostfxr_path_with_assembly_path(const PdCString& assembly_path);
    
    /**
     * 获取 hostfxr 库的路径
     * 在指定的 dotnet 根目录下搜索
     */
    static PdCString get_hostfxr_path_with_dotnet_root(const PdCString& dotnet_root);
    
    /**
     * 加载 hostfxr 库
     */
    static std::shared_ptr<Hostfxr> load_hostfxr();
    
    /**
     * 使用指定的程序集路径加载 hostfxr 库
     */
    static std::shared_ptr<Hostfxr> load_hostfxr_with_assembly_path(const PdCString& assembly_path);
    
    /**
     * 从指定的 dotnet 根目录加载 hostfxr 库
     */
    static std::shared_ptr<Hostfxr> load_hostfxr_with_dotnet_root(const PdCString& dotnet_root);

private:
    static PdCString get_hostfxr_path_internal(const bindings::get_hostfxr_parameters* parameters);
    static bindings::get_hostfxr_path_fn load_nethost_function();
};

} // namespace netcorehost

#endif // NETCOREHOST_NETHOST_HPP

