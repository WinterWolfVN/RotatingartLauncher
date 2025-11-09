#include "netcorehost/context.hpp"
#include "netcorehost/hostfxr.hpp"
#include "netcorehost/delegate_loader.hpp"

namespace netcorehost {

// HostfxrContext 实现

HostfxrContext::HostfxrContext(
    bindings::hostfxr_handle handle,
    std::shared_ptr<Hostfxr> hostfxr,
    bool is_primary
) : handle_(handle), hostfxr_(hostfxr), is_primary_(is_primary), closed_(false) {}

HostfxrContext::~HostfxrContext() {
    if (!closed_ && handle_) {
        try {
            close();
        } catch (...) {
            // 析构函数中忽略异常
        }
    }
}

void* HostfxrContext::get_runtime_delegate(bindings::hostfxr_delegate_type type) {
    void* delegate = nullptr;
    
    int32_t result = hostfxr_->get_get_runtime_delegate_fn()(
        handle_,
        type,
        &delegate
    );
    
    auto hosting_result = HostingResult::from_status_code(result);
    hosting_result.throw_if_error();
    
    return delegate;
}

std::unique_ptr<DelegateLoader> HostfxrContext::get_delegate_loader() {
    auto load_assembly_and_get_function_pointer = 
        reinterpret_cast<bindings::load_assembly_and_get_function_pointer_fn>(
            get_runtime_delegate(bindings::hostfxr_delegate_type::hdt_load_assembly_and_get_function_pointer)
        );
    
    bindings::get_function_pointer_fn get_function_pointer = nullptr;
    
    // 尝试获取 get_function_pointer 委托（.NET 5.0+）
    try {
        get_function_pointer = reinterpret_cast<bindings::get_function_pointer_fn>(
            get_runtime_delegate(bindings::hostfxr_delegate_type::hdt_get_function_pointer)
        );
    } catch (...) {
        // 如果不支持，保持为 nullptr
    }
    
    return std::make_unique<DelegateLoader>(
        load_assembly_and_get_function_pointer,
        get_function_pointer
    );
}

std::unique_ptr<AssemblyDelegateLoader> HostfxrContext::get_delegate_loader_for_assembly(
    const PdCString& assembly_path
) {
    auto loader = get_delegate_loader();
    return std::make_unique<AssemblyDelegateLoader>(std::move(loader), assembly_path);
}

void HostfxrContext::close() {
    if (!closed_ && handle_) {
        int32_t result = hostfxr_->get_close_fn()(handle_);
        auto hosting_result = HostingResult::from_status_code(result);
        hosting_result.throw_if_error();
        
        handle_ = nullptr;
        closed_ = true;
    }
}

// HostfxrContextForRuntimeConfig 实现

void HostfxrContextForRuntimeConfig::load_assembly_from_path(const PdCString& assembly_path) {
    auto load_assembly = reinterpret_cast<bindings::load_assembly_fn>(
        get_runtime_delegate(bindings::hostfxr_delegate_type::hdt_load_assembly)
    );
    
    int32_t result = load_assembly(assembly_path.c_str(), nullptr, nullptr);
    auto hosting_result = HostingResult::from_status_code(result);
    hosting_result.throw_if_error();
}

void HostfxrContextForRuntimeConfig::load_assembly_from_bytes(
    const uint8_t* assembly_bytes,
    size_t assembly_size,
    const uint8_t* symbols_bytes,
    size_t symbols_size
) {
    auto load_assembly_bytes = reinterpret_cast<bindings::load_assembly_bytes_fn>(
        get_runtime_delegate(bindings::hostfxr_delegate_type::hdt_load_assembly_bytes)
    );
    
    int32_t result = load_assembly_bytes(
        assembly_bytes,
        assembly_size,
        symbols_bytes,
        symbols_size,
        nullptr,
        nullptr
    );
    
    auto hosting_result = HostingResult::from_status_code(result);
    hosting_result.throw_if_error();
}

// HostfxrContextForCommandLine 实现

AppOrHostingResult HostfxrContextForCommandLine::run_app() {
    int32_t result = hostfxr_->get_run_app_fn()(handle_);
    return AppOrHostingResult(result);
}

} // namespace netcorehost

