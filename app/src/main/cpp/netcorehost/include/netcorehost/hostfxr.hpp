#ifndef NETCOREHOST_HOSTFXR_HPP
#define NETCOREHOST_HOSTFXR_HPP

#include <string>
#include <memory>
#include <functional>
#include "bindings.hpp"
#include "pdcstring.hpp"
#include "error.hpp"

namespace netcorehost {

// 前向声明
class HostfxrContext;
class HostfxrContextForRuntimeConfig;
class HostfxrContextForCommandLine;

/**
 * Hostfxr 库包装器
 */
class Hostfxr : public std::enable_shared_from_this<Hostfxr> {
public:
    ~Hostfxr();
    
    /**
     * 从指定路径加载 hostfxr 库
     */
    static std::shared_ptr<Hostfxr> load_from_path(const PdCString& path);
    
    /**
     * 为运行时配置初始化上下文
     * 用于加载和调用托管函数
     */
    std::unique_ptr<HostfxrContextForRuntimeConfig> initialize_for_runtime_config(
        const PdCString& runtime_config_path
    );
    
    /**
     * 为 dotnet 命令行初始化上下文
     * 用于运行应用程序
     */
    std::unique_ptr<HostfxrContextForCommandLine> initialize_for_dotnet_command_line(
        const PdCString& assembly_path
    );
    
    /**
     * 为 dotnet 命令行初始化上下文（指定 dotnet_root）
     * 用于运行应用程序
     */
    std::unique_ptr<HostfxrContextForCommandLine> initialize_for_dotnet_command_line_with_dotnet_root(
        const PdCString& assembly_path,
        const PdCString& dotnet_root
    );

    /**
     * 为 dotnet 命令行初始化上下文（支持传递参数）
     * 用于运行应用程序并传递命令行参数到 Main(string[] args)
     */
    std::unique_ptr<HostfxrContextForCommandLine> initialize_for_dotnet_command_line_with_args(
        const PdCString& assembly_path,
        int argc,
        const char* const* argv
    );

    /**
     * 为 dotnet 命令行初始化上下文（支持传递参数和 dotnet_root）
     * 用于运行应用程序并传递命令行参数到 Main(string[] args)
     */
    std::unique_ptr<HostfxrContextForCommandLine> initialize_for_dotnet_command_line_with_args_and_dotnet_root(
        const PdCString& assembly_path,
        int argc,
        const char* const* argv,
        const PdCString& dotnet_root
    );

    /**
     * 获取 dotnet 根目录路径
     */
    std::string get_dotnet_root() const;
    
    /**
     * 获取 dotnet 可执行文件路径
     */
    std::string get_dotnet_exe() const;
    
    // 内部使用
    bindings::hostfxr_initialize_for_runtime_config_fn get_initialize_for_runtime_config_fn() const {
        return initialize_for_runtime_config_fn_;
    }
    
    bindings::hostfxr_initialize_for_dotnet_command_line_fn get_initialize_for_dotnet_command_line_fn() const {
        return initialize_for_dotnet_command_line_fn_;
    }
    
    bindings::hostfxr_get_runtime_delegate_fn get_get_runtime_delegate_fn() const {
        return get_runtime_delegate_fn_;
    }
    
    bindings::hostfxr_run_app_fn get_run_app_fn() const {
        return run_app_fn_;
    }
    
    bindings::hostfxr_close_fn get_close_fn() const {
        return close_fn_;
    }

private:
    Hostfxr(void* library_handle, const PdCString& hostfxr_path);
    
    void* library_handle_;
    PdCString hostfxr_path_;
    PdCString dotnet_exe_path_;
    
    // 函数指针
    bindings::hostfxr_initialize_for_runtime_config_fn initialize_for_runtime_config_fn_;
    bindings::hostfxr_initialize_for_dotnet_command_line_fn initialize_for_dotnet_command_line_fn_;
    bindings::hostfxr_get_runtime_delegate_fn get_runtime_delegate_fn_;
    bindings::hostfxr_run_app_fn run_app_fn_;
    bindings::hostfxr_close_fn close_fn_;
    
    void load_functions();
    static PdCString find_dotnet_exe(const PdCString& hostfxr_path);
};

} // namespace netcorehost

#endif // NETCOREHOST_HOSTFXR_HPP

