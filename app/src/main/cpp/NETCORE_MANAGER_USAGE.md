# .NET Core Host Manager 使用指南

## 概述

`netcorehost_manager` 提供了一个强大的 .NET 程序集管理和调用框架，支持：

1. **一次初始化，多次使用** - 只需初始化一次运行时环境
2. **运行多个程序集** - 每个程序集在独立上下文中运行，互不干扰
3. **调用任意方法** - 可以调用程序集的任意静态方法
4. **灵活的参数和返回值** - 支持委托类型的参数和返回值

## API 概览

### 1. 初始化运行时（只需调用一次）

```c
int netcore_init(const char* dotnet_root, int framework_major);
```

- `dotnet_root`: .NET 运行时根目录
- `framework_major`: 框架主版本号（8 = .NET 8，0 = 自动检测最高版本）
- **返回值**: 0 成功，负数失败

**示例：**
```c
// 初始化 .NET 8 运行时
netcore_init("/data/data/com.app.ralaunch/files/dotnet", 8);
```

### 2. 运行程序集（调用 Main 入口点）

```c
int netcore_run_app(
    const char* app_dir,
    const char* main_assembly,
    int argc,
    const char* const* argv);
```

**示例：**
```c
// 运行游戏
const char* args[] = {"--fullscreen", "--difficulty=hard"};
int exit_code = netcore_run_app(
    "/sdcard/Games/MyGame",
    "MyGame.dll",
    2,
    args
);
```

### 3. 加载程序集（用于调用方法）

```c
int netcore_load_assembly(
    const char* app_dir,
    const char* assembly_name,
    void** context_handle);
```

**示例：**
```c
void* ctx;
netcore_load_assembly("/sdcard/Games/MyMod", "ModLoader.dll", &ctx);
```

### 4. 调用静态方法

```c
int netcore_call_method(
    void* context_handle,
    const char* type_name,
    const char* method_name,
    const char* delegate_type,
    void** result);
```

**类型名格式**: `"命名空间.类名, 程序集名"`

**示例：**

#### 调用无返回值的方法
```c
// C# 代码：
// namespace MyMod {
//     public class Startup {
//         public static void Initialize() { ... }
//     }
// }

netcore_call_method(ctx, "MyMod.Startup, ModLoader", "Initialize", nullptr, nullptr);
```

#### 调用有返回值的方法（返回委托）
```c
// C# 代码：
// namespace MyMod {
//     public delegate int AddDelegate(int a, int b);
//     public class Math {
//         public static AddDelegate GetAddFunction() {
//             return (a, b) => a + b;
//         }
//     }
// }

typedef int (*add_fn)(int, int);
add_fn add_func;
netcore_call_method(
    ctx,
    "MyMod.Math, ModLoader",
    "GetAddFunction",
    "MyMod.AddDelegate, ModLoader",
    (void**)&add_func
);

// 现在可以调用返回的委托
int result = add_func(5, 3); // result = 8
```

### 5. 获取属性值

```c
int netcore_get_property(
    void* context_handle,
    const char* type_name,
    const char* property_name,
    const char* delegate_type,
    void** result);
```

**示例：**
```c
// C# 代码：
// namespace MyMod {
//     public class Config {
//         public static string Version { get; } = "1.0.0";
//     }
// }

typedef const char* (*get_string_fn)();
get_string_fn get_version;
netcore_get_property(
    ctx,
    "MyMod.Config, ModLoader",
    "Version",
    "System.Func`1[[System.String]], System.Private.CoreLib",
    (void**)&get_version
);

const char* version = get_version();
```

### 6. 关闭上下文

```c
void netcore_close_context(void* context_handle);
```

**示例：**
```c
netcore_close_context(ctx);
```

### 7. 清理所有资源

```c
void netcore_cleanup();
```

## 完整使用示例

### 示例 1：运行多个独立的程序集

```c
// 初始化（只需一次）
netcore_init("/data/data/com.app.ralaunch/files/dotnet", 8);

// 运行游戏 1
netcore_run_app("/sdcard/Games/Game1", "Game1.dll", 0, nullptr);

// 运行游戏 2（完全独立）
netcore_run_app("/sdcard/Games/Game2", "Game2.dll", 0, nullptr);

// 清理
netcore_cleanup();
```

### 示例 2：加载 ModLoader 并调用多个方法

```c
// 初始化
netcore_init("/data/data/com.app.ralaunch/files/dotnet", 8);

// 加载 ModLoader
void* mod_ctx;
netcore_load_assembly("/sdcard/Games/Modded", "ModLoader.dll", &mod_ctx);

// 调用初始化方法
netcore_call_method(mod_ctx, "ModLoader.Core, ModLoader", "Initialize", nullptr, nullptr);

// 获取游戏启动函数
typedef void (*start_game_fn)(const char*);
start_game_fn start_game;
netcore_call_method(
    mod_ctx,
    "ModLoader.Core, ModLoader",
    "GetGameStarter",
    "ModLoader.GameStartDelegate, ModLoader",
    (void**)&start_game
);

// 调用游戏启动函数
start_game("/sdcard/Games/Modded/Game.dll");

// 关闭上下文
netcore_close_context(mod_ctx);
netcore_cleanup();
```

### 示例 3：同时管理多个程序集上下文

```c
netcore_init("/data/data/com.app.ralaunch/files/dotnet", 8);

// 加载多个 Mod
void* mod1_ctx, *mod2_ctx, *mod3_ctx;
netcore_load_assembly("/sdcard/Mods/Mod1", "Mod1.dll", &mod1_ctx);
netcore_load_assembly("/sdcard/Mods/Mod2", "Mod2.dll", &mod2_ctx);
netcore_load_assembly("/sdcard/Mods/Mod3", "Mod3.dll", &mod3_ctx);

// 分别调用各个 Mod 的方法（互不干扰）
netcore_call_method(mod1_ctx, "Mod1.Entry, Mod1", "Load", nullptr, nullptr);
netcore_call_method(mod2_ctx, "Mod2.Entry, Mod2", "Load", nullptr, nullptr);
netcore_call_method(mod3_ctx, "Mod3.Entry, Mod3", "Load", nullptr, nullptr);

// 清理
netcore_close_context(mod1_ctx);
netcore_close_context(mod2_ctx);
netcore_close_context(mod3_ctx);
netcore_cleanup();
```

## 错误处理

```c
const char* netcore_get_last_error();
```

**示例：**
```c
if (netcore_init(dotnet_root, 8) != 0) {
    const char* error = netcore_get_last_error();
    printf("初始化失败: %s\n", error);
}
```

## 注意事项

1. **必须先初始化**: 调用任何其他函数前，必须先调用 `netcore_init()`
2. **上下文生命周期**: 使用完上下文后应调用 `netcore_close_context()` 释放资源
3. **线程安全**: 当前实现不保证线程安全，建议在单线程中使用
4. **委托生命周期**: 获取的委托指针在上下文关闭后失效
5. **工作目录**: 每次调用 `run_app` 或 `load_assembly` 会自动设置工作目录

## C# 端最佳实践

为了方便从 C/C++ 调用，C# 代码应遵循以下规范：

### 1. 使用静态方法和属性
```csharp
public class ModLoader {
    public static void Initialize() { }
    public static string Version { get; } = "1.0.0";
}
```

### 2. 定义清晰的委托类型
```csharp
public delegate void GameStartDelegate(string gamePath);
public delegate int MathOperation(int a, int b);
```

### 3. 提供友好的入口点
```csharp
public class ModEntry {
    public static void Load() {
        // Mod 加载逻辑
    }

    public static GameStartDelegate GetGameStarter() {
        return (path) => StartGame(path);
    }

    private static void StartGame(string path) {
        // 游戏启动逻辑
    }
}
```

## 性能提示

1. **重用上下文**: 如果需要多次调用同一程序集的方法，只加载一次
2. **避免频繁创建/销毁**: 尽量长时间保持上下文活跃
3. **批量操作**: 在一个上下文中完成所有相关操作后再关闭

## 故障排查

### 问题：找不到程序集
- 确保 `.dll` 和 `.runtimeconfig.json` 都存在
- 检查文件路径是否正确
- 查看日志中的详细错误信息

### 问题：方法调用失败
- 确认类型名格式正确：`"命名空间.类名, 程序集名"`
- 确保方法是 `public static`
- 检查委托类型字符串是否匹配

### 问题：返回的委托无法调用
- 确保委托签名与 C 函数指针匹配
- 检查上下文是否仍然有效
