# netcorehost 使用示例

## 简介
netcorehost 是一个 C++ 库，用于在原生应用中托管 .NET Core 运行时。

## 基本使用

### 1. 包含头文件
```cpp
#include <netcorehost/nethost.hpp>
#include <netcorehost/hostfxr.hpp>
#include <netcorehost/context.hpp>
#include <netcorehost/delegate_loader.hpp>
#include <netcorehost/error.hpp>
```

### 2. 加载 .NET 运行时
```cpp
try {
    // 加载 hostfxr 库
    auto hostfxr = netcorehost::Nethost::load_hostfxr();
    
    // 初始化运行时配置
    netcorehost::PdCString config_path = 
        netcorehost::PdCString::from_str("/path/to/your/app.runtimeconfig.json");
    
    auto context = hostfxr->initialize_for_runtime_config(config_path);
    
    // 现在可以使用 context 调用托管代码
    
} catch (const netcorehost::HostingException& ex) {
    // 处理错误
    std::cerr << "Failed to load .NET runtime: " << ex.what() << std::endl;
}
```

### 3. 调用托管函数
```cpp
try {
    // 获取委托加载器
    auto loader = context->get_delegate_loader();
    
    // 加载程序集并获取函数指针
    netcorehost::PdCString assembly_path = 
        netcorehost::PdCString::from_str("/path/to/YourAssembly.dll");
    netcorehost::PdCString type_name = 
        netcorehost::PdCString::from_str("YourNamespace.YourClass, YourAssembly");
    netcorehost::PdCString method_name = 
        netcorehost::PdCString::from_str("YourMethod");
    
    auto function = loader->get_function_with_default_signature(
        assembly_path, type_name, method_name);
    
    // 调用函数
    // 注意：函数签名必须匹配 ComponentEntryPoint 类型
    // 即：int (*)(void* arg, int arg_size_in_bytes)
    int result = function(nullptr, 0);
    
} catch (const netcorehost::HostingException& ex) {
    std::cerr << "Failed to call managed function: " << ex.what() << std::endl;
}
```

### 4. 从路径加载程序集
```cpp
try {
    // 使用运行时配置上下文加载程序集
    auto runtime_context = 
        dynamic_cast<netcorehost::HostfxrContextForRuntimeConfig*>(context.get());
    
    if (runtime_context) {
        netcorehost::PdCString assembly_path = 
            netcorehost::PdCString::from_str("/path/to/YourAssembly.dll");
        runtime_context->load_assembly_from_path(assembly_path);
    }
    
} catch (const netcorehost::HostingException& ex) {
    std::cerr << "Failed to load assembly: " << ex.what() << std::endl;
}
```

### 5. 运行 .NET 应用程序
```cpp
try {
    // 使用命令行参数初始化
    netcorehost::PdCString app_path = 
        netcorehost::PdCString::from_str("/path/to/YourApp.dll");
    
    auto cmd_context = hostfxr->initialize_for_dotnet_command_line(app_path);
    
    // 运行应用
    auto result = cmd_context->run_app();
    
    if (result.is_success()) {
        std::cout << "App completed successfully" << std::endl;
    } else {
        std::cerr << "App failed with error: " << result.get_error_message() << std::endl;
    }
    
} catch (const netcorehost::HostingException& ex) {
    std::cerr << "Failed to run app: " << ex.what() << std::endl;
}
```

## Android 特定注意事项

在 Android 上使用 netcorehost 时，需要注意：

1. **运行时路径**: 确保 .NET 运行时库（nethost.so, hostfxr.so）在 APK 中正确打包
2. **权限**: 可能需要文件访问权限来读取程序集和配置文件
3. **路径转换**: Android 使用 Unix 风格的路径，使用 `PdCString::from_str()` 处理路径字符串

## 错误处理

所有可能失败的操作都会抛出 `HostingException`。建议使用 try-catch 块包装所有 netcorehost API 调用：

```cpp
try {
    // netcorehost API 调用
} catch (const netcorehost::HostingException& ex) {
    // 获取错误代码
    uint32_t error_code = ex.error_code();
    
    // 获取错误消息
    std::string message = ex.what();
    
    // 处理错误...
}
```

## 完整示例

请参考原始 netcorehost 项目的 examples 目录：
- call-managed-function
- passing-parameters
- return-string-from-managed
- run-app
- run-app-with-args

这些示例展示了各种使用场景。

