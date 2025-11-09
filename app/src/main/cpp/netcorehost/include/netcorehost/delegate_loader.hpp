#ifndef NETCOREHOST_DELEGATE_LOADER_HPP
#define NETCOREHOST_DELEGATE_LOADER_HPP

#include <memory>
#include <type_traits>
#include "bindings.hpp"
#include "pdcstring.hpp"
#include "error.hpp"

namespace netcorehost {

/**
 * 委托加载器 - 用于从托管程序集加载函数指针
 */
class DelegateLoader {
public:
    DelegateLoader(
        bindings::load_assembly_and_get_function_pointer_fn load_assembly_and_get_function_pointer,
        bindings::get_function_pointer_fn get_function_pointer = nullptr
    );
    
    /**
     * 加载程序集并获取函数指针
     * @param assembly_path 程序集路径
     * @param type_name 完全限定的类型名称（例如：MyNamespace.MyClass, MyAssembly）
     * @param method_name 方法名称
     * @param delegate_type_name 委托类型的完全限定名称
     * @return 函数指针
     */
    template<typename T>
    T get_function(
        const PdCString& assembly_path,
        const PdCString& type_name,
        const PdCString& method_name,
        const PdCString& delegate_type_name
    ) {
        static_assert(std::is_pointer<T>::value, "Template parameter must be a function pointer type");
        
        void* delegate = nullptr;
        
        int32_t result = load_assembly_and_get_function_pointer_(
            assembly_path.c_str(),
            type_name.c_str(),
            method_name.c_str(),
            delegate_type_name.c_str(),
            nullptr,
            &delegate
        );
        
        auto hosting_result = HostingResult::from_status_code(result);
        hosting_result.throw_if_error();
        
        return reinterpret_cast<T>(delegate);
    }
    
    /**
     * 使用默认签名加载函数
     * 默认签名：int32_t Function(void* arg, int32_t arg_size_bytes)
     */
    bindings::component_entry_point_fn get_function_with_default_signature(
        const PdCString& assembly_path,
        const PdCString& type_name,
        const PdCString& method_name
    );
    
    /**
     * 加载带有 UnmanagedCallersOnly 属性的函数
     * 这些函数不需要指定委托类型
     */
    template<typename T>
    T get_function_with_unmanaged_callers_only(
        const PdCString& assembly_path,
        const PdCString& type_name,
        const PdCString& method_name
    ) {
        static_assert(std::is_pointer<T>::value, "Template parameter must be a function pointer type");
        
        void* delegate = nullptr;
        
        int32_t result = load_assembly_and_get_function_pointer_(
            assembly_path.c_str(),
            type_name.c_str(),
            method_name.c_str(),
            PDCSTR(""),  // UnmanagedCallersOnly 不需要委托类型
            nullptr,
            &delegate
        );
        
        auto hosting_result = HostingResult::from_status_code(result);
        hosting_result.throw_if_error();
        
        return reinterpret_cast<T>(delegate);
    }
    
    /**
     * 从已加载的程序集获取函数指针 (.NET 5.0+)
     * 需要在调用此函数之前先使用 load_assembly_and_get_function_pointer 加载程序集
     */
    template<typename T>
    T get_function_pointer(
        const PdCString& type_name,
        const PdCString& method_name,
        const PdCString& delegate_type_name
    ) {
        static_assert(std::is_pointer<T>::value, "Template parameter must be a function pointer type");
        
        if (!get_function_pointer_) {
            throw HostingException(
                HostingError::HostApiUnsupportedVersion,
                "get_function_pointer is not supported in this .NET version"
            );
        }
        
        void* delegate = nullptr;
        
        int32_t result = get_function_pointer_(
            type_name.c_str(),
            method_name.c_str(),
            delegate_type_name.c_str(),
            nullptr,
            nullptr,
            &delegate
        );
        
        auto hosting_result = HostingResult::from_status_code(result);
        hosting_result.throw_if_error();
        
        return reinterpret_cast<T>(delegate);
    }

private:
    bindings::load_assembly_and_get_function_pointer_fn load_assembly_and_get_function_pointer_;
    bindings::get_function_pointer_fn get_function_pointer_;
};

/**
 * 程序集委托加载器 - 用于从特定程序集加载函数
 */
class AssemblyDelegateLoader {
public:
    AssemblyDelegateLoader(
        std::unique_ptr<DelegateLoader> loader,
        const PdCString& assembly_path
    );
    
    /**
     * 获取函数指针
     */
    template<typename T>
    T get_function(
        const PdCString& type_name,
        const PdCString& method_name,
        const PdCString& delegate_type_name
    ) {
        return loader_->get_function<T>(
            assembly_path_,
            type_name,
            method_name,
            delegate_type_name
        );
    }
    
    /**
     * 使用默认签名获取函数
     */
    bindings::component_entry_point_fn get_function_with_default_signature(
        const PdCString& type_name,
        const PdCString& method_name
    );
    
    /**
     * 获取带有 UnmanagedCallersOnly 属性的函数
     */
    template<typename T>
    T get_function_with_unmanaged_callers_only(
        const PdCString& type_name,
        const PdCString& method_name
    ) {
        return loader_->get_function_with_unmanaged_callers_only<T>(
            assembly_path_,
            type_name,
            method_name
        );
    }

private:
    std::unique_ptr<DelegateLoader> loader_;
    PdCString assembly_path_;
};

} // namespace netcorehost

#endif // NETCOREHOST_DELEGATE_LOADER_HPP

