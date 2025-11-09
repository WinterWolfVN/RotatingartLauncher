#include "netcorehost/delegate_loader.hpp"

namespace netcorehost {

// DelegateLoader 实现

DelegateLoader::DelegateLoader(
    bindings::load_assembly_and_get_function_pointer_fn load_assembly_and_get_function_pointer,
    bindings::get_function_pointer_fn get_function_pointer
) : load_assembly_and_get_function_pointer_(load_assembly_and_get_function_pointer),
    get_function_pointer_(get_function_pointer) {}

bindings::component_entry_point_fn DelegateLoader::get_function_with_default_signature(
    const PdCString& assembly_path,
    const PdCString& type_name,
    const PdCString& method_name
) {
    void* delegate = nullptr;
    
    // 默认委托类型是 null，表示使用默认的 ComponentEntryPoint 签名
    int32_t result = load_assembly_and_get_function_pointer_(
        assembly_path.c_str(),
        type_name.c_str(),
        method_name.c_str(),
        nullptr,  // 使用默认委托类型
        nullptr,
        &delegate
    );
    
    auto hosting_result = HostingResult::from_status_code(result);
    hosting_result.throw_if_error();
    
    return reinterpret_cast<bindings::component_entry_point_fn>(delegate);
}

// AssemblyDelegateLoader 实现

AssemblyDelegateLoader::AssemblyDelegateLoader(
    std::unique_ptr<DelegateLoader> loader,
    const PdCString& assembly_path
) : loader_(std::move(loader)), assembly_path_(assembly_path) {}

bindings::component_entry_point_fn AssemblyDelegateLoader::get_function_with_default_signature(
    const PdCString& type_name,
    const PdCString& method_name
) {
    return loader_->get_function_with_default_signature(
        assembly_path_,
        type_name,
        method_name
    );
}

} // namespace netcorehost

