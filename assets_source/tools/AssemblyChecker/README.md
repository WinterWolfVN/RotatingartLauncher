# AssemblyChecker - 程序集检查工具

## 功能

- 检测 .NET 程序集是否有 Main 入口点
- 获取程序集元数据（名称、版本、入口点方法等）
- 检测程序集是否有嵌入图标
- 提取程序集图标（计划中）

## 构建

```bash
cd assets_source/tools/AssemblyChecker
chmod +x build.sh
./build.sh
```

构建完成后，将 `bin/publish` 目录下的所有文件复制到 `app/src/main/assets/tools/AssemblyChecker/`

## 使用方法

### 命令行使用

```bash
dotnet AssemblyChecker.dll <assembly_path> [--extract-icon <output_path>]
```

**示例：**
```bash
# 检查程序集
dotnet AssemblyChecker.dll /path/to/MyGame.dll

# 检查并提取图标
dotnet AssemblyChecker.dll /path/to/MyGame.dll --extract-icon /path/to/icon.png
```

### Java 调用

```java
import com.app.ralaunch.utils.AssemblyChecker;

// 检查程序集
AssemblyChecker.CheckResult result = AssemblyChecker.checkAssembly(
    context,
    "/path/to/MyGame.dll"
);

if (result.hasEntryPoint) {
    System.out.println("程序集有入口点: " + result.entryPointMethod);
    System.out.println("程序集名称: " + result.assemblyName);
    System.out.println("程序集版本: " + result.assemblyVersion);
} else {
    System.out.println("程序集没有入口点");
}

// 快速检查
boolean hasEntry = AssemblyChecker.hasEntryPoint(context, "/path/to/MyGame.dll");
```

## 输出格式

JSON 格式输出：

```json
{
  "AssemblyPath": "/path/to/MyGame.dll",
  "Exists": true,
  "IsNetAssembly": true,
  "HasEntryPoint": true,
  "EntryPointToken": "0x12345678",
  "EntryPointMethod": "MyNamespace.Program::Main",
  "AssemblyName": "MyGame",
  "AssemblyVersion": "1.0.0.0",
  "HasIcon": true,
  "IconExtracted": false,
  "IconPath": null,
  "IconExtractionError": null,
  "Error": null
}
```

## 退出码

- `0`: 程序集有入口点
- `1`: 程序集没有入口点（但检测成功）
- `2`: 检测失败（文件不存在、不是 .NET 程序集等）

## 部署

1. 构建工具：`./build.sh`
2. 复制到 assets：
   ```bash
   cp -r bin/publish/* ../../../app/src/main/assets/tools/AssemblyChecker/
   ```
3. 工具会在首次使用时自动从 assets 提取到内部存储

## 技术细节

- 使用 `System.Reflection.Metadata` 读取程序集元数据
- 使用 `PEReader` 解析 PE 文件
- 不需要加载程序集，避免依赖问题
- 跨平台，支持 Android

## 已知限制

- 图标提取功能尚未完全实现（需要解析 Win32 资源树）
- 仅支持 .NET 程序集（不支持原生 PE 文件）
