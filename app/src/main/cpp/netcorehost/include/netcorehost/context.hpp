#ifndef NETCOREHOST_CONTEXT_HPP
#define NETCOREHOST_CONTEXT_HPP

#include <memory>
#include "bindings.hpp"
#include "error.hpp"
#include "pdcstring.hpp"

namespace netcorehost {

// 前向声明
class Hostfxr;
class DelegateLoader;
class AssemblyDelegateLoader;

/**
 * Hostfxr 上下文基类
 */
class HostfxrContext {
public:
    virtual ~HostfxrContext();
    
    /**
     * 获取原始上下文句柄
     */
    bindings::hostfxr_handle handle() const { return handle_; }
    
    /**
     * 判断是否为主上下文
     */
    bool is_primary() const { return is_primary_; }
    
    /**
     * 获取运行时委托
     */
    void* get_runtime_delegate(bindings::hostfxr_delegate_type type);
    
    /**
     * 获取委托加载器
     */
    std::unique_ptr<DelegateLoader> get_delegate_loader();
    
    /**
     * 获取指定程序集的委托加载器
     */
    std::unique_ptr<AssemblyDelegateLoader> get_delegate_loader_for_assembly(
        const PdCString& assembly_path
    );
    
    /**
     * 显式关闭上下文
     */
    void close();

protected:
    HostfxrContext(
        bindings::hostfxr_handle handle,
        std::shared_ptr<Hostfxr> hostfxr,
        bool is_primary
    );
    
    bindings::hostfxr_handle handle_;
    std::shared_ptr<Hostfxr> hostfxr_;
    bool is_primary_;
    bool closed_;
};

/**
 * 为运行时配置初始化的上下文
 * 用于加载程序集和调用托管函数
 */
class HostfxrContextForRuntimeConfig : public HostfxrContext {
public:
    HostfxrContextForRuntimeConfig(
        bindings::hostfxr_handle handle,
        std::shared_ptr<Hostfxr> hostfxr,
        bool is_primary
    ) : HostfxrContext(handle, hostfxr, is_primary) {}
    
    /**
     * 从路径加载程序集 (.NET 8.0+)
     */
    void load_assembly_from_path(const PdCString& assembly_path);
    
    /**
     * 从字节数组加载程序集 (.NET 8.0+)
     */
    void load_assembly_from_bytes(
        const uint8_t* assembly_bytes,
        size_t assembly_size,
        const uint8_t* symbols_bytes = nullptr,
        size_t symbols_size = 0
    );
};

/**
 * 为命令行初始化的上下文
 * 用于运行应用程序
 */
class HostfxrContextForCommandLine : public HostfxrContext {
public:
    HostfxrContextForCommandLine(
        bindings::hostfxr_handle handle,
        std::shared_ptr<Hostfxr> hostfxr,
        bool is_primary
    ) : HostfxrContext(handle, hostfxr, is_primary) {}
    
    /**
     * 运行应用程序
     * @return 应用程序退出码或托管错误码
     */
    AppOrHostingResult run_app();
};

} // namespace netcorehost

#endif // NETCOREHOST_CONTEXT_HPP

