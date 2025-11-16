using System;
using System.IO;
using System.Reflection;
using System.Reflection.Metadata;
using System.Reflection.Metadata.Ecma335;
using System.Reflection.PortableExecutable;
using System.Text.Json;

namespace AssemblyChecker;

/// <summary>
/// 程序集检查器 - 检测 .NET 程序集的入口点和图标
/// </summary>
class Program
{
    static int Main(string[] args)
    {
        if (args.Length < 1)
        {
            Console.Error.WriteLine("用法: AssemblyChecker <assembly_path> [--extract-icon <output_path>]");
            return 2;
        }

        string assemblyPath = args[0];
        string? iconOutputPath = null;

        // 解析参数
        for (int i = 1; i < args.Length; i++)
        {
            if (args[i] == "--extract-icon" && i + 1 < args.Length)
            {
                iconOutputPath = args[i + 1];
                i++;
            }
        }

        try
        {
            var result = CheckAssembly(assemblyPath, iconOutputPath);

            // 输出 JSON 结果
            var json = JsonSerializer.Serialize(result, new JsonSerializerOptions
            {
                WriteIndented = false
            });
            Console.WriteLine(json);

            // 返回退出码
            return result.HasEntryPoint ? 0 : 1;
        }
        catch (Exception ex)
        {
            var errorResult = new CheckResult
            {
                Error = ex.Message,
                HasEntryPoint = false
            };

            Console.WriteLine(JsonSerializer.Serialize(errorResult));
            return 2;
        }
    }

    static CheckResult CheckAssembly(string assemblyPath, string? iconOutputPath)
    {
        var result = new CheckResult
        {
            AssemblyPath = assemblyPath,
            Exists = File.Exists(assemblyPath)
        };

        if (!result.Exists)
        {
            result.Error = "文件不存在";
            return result;
        }

        try
        {
            // 使用 PEReader 读取程序集元数据
            using var fileStream = File.OpenRead(assemblyPath);
            using var peReader = new PEReader(fileStream);

            if (!peReader.HasMetadata)
            {
                result.Error = "不是有效的 .NET 程序集";
                return result;
            }

            var metadataReader = peReader.GetMetadataReader();
            result.IsNetAssembly = true;

            // 检查入口点（从 CorHeader 获取）
            var corHeader = peReader.PEHeaders.CorHeader;
            if (corHeader == null)
            {
                result.Error = "不是有效的 .NET 程序集（缺少 CorHeader）";
                return result;
            }

            int entryPointToken = corHeader.EntryPointTokenOrRelativeVirtualAddress;
            var entryPoint = MetadataTokens.EntityHandle(entryPointToken);
            result.HasEntryPoint = !entryPoint.IsNil && entryPointToken != 0;

            if (result.HasEntryPoint)
            {
                result.EntryPointToken = $"0x{entryPoint.GetHashCode():X8}";

                // 获取入口点方法信息
                try
                {
                    if (entryPoint.Kind == HandleKind.MethodDefinition)
                    {
                        var methodDef = metadataReader.GetMethodDefinition((MethodDefinitionHandle)entryPoint);
                        var typeDef = metadataReader.GetTypeDefinition(methodDef.GetDeclaringType());

                        string typeName = metadataReader.GetString(typeDef.Name);
                        string methodName = metadataReader.GetString(methodDef.Name);
                        string namespaceName = metadataReader.GetString(typeDef.Namespace);

                        result.EntryPointMethod = $"{namespaceName}.{typeName}::{methodName}";
                    }
                }
                catch
                {
                    // 忽略入口点详细信息提取失败
                }
            }

            // 获取程序集名称和版本
            var assemblyDef = metadataReader.GetAssemblyDefinition();
            result.AssemblyName = metadataReader.GetString(assemblyDef.Name);
            result.AssemblyVersion = assemblyDef.Version.ToString();

            // 检查是否有嵌入的图标资源
            result.HasIcon = HasIconResource(peReader);

            // 如果需要提取图标
            if (result.HasIcon && iconOutputPath != null)
            {
                try
                {
                    ExtractIcon(peReader, iconOutputPath);
                    result.IconExtracted = true;
                    result.IconPath = iconOutputPath;
                }
                catch (Exception ex)
                {
                    result.IconExtractionError = ex.Message;
                }
            }

            return result;
        }
        catch (BadImageFormatException)
        {
            result.Error = "不是有效的 PE 文件或 .NET 程序集";
            return result;
        }
        catch (Exception ex)
        {
            result.Error = $"读取程序集失败: {ex.Message}";
            return result;
        }
    }

    static bool HasIconResource(PEReader peReader)
    {
        try
        {
            var headers = peReader.PEHeaders;
            var resourcesDirectory = headers.PEHeader?.ResourceTableDirectory;

            return resourcesDirectory.HasValue && resourcesDirectory.Value.Size > 0;
        }
        catch
        {
            return false;
        }
    }

    static void ExtractIcon(PEReader peReader, string outputPath)
    {
        // 简化的图标提取（仅提取第一个图标资源）
        var headers = peReader.PEHeaders;
        var resourcesDirectory = headers.PEHeader?.ResourceTableDirectory;

        if (!resourcesDirectory.HasValue || resourcesDirectory.Value.Size == 0)
        {
            throw new Exception("没有找到图标资源");
        }

        // 注意：完整的图标提取需要解析 Win32 资源树
        // 这里提供一个简化实现的占位符
        throw new NotImplementedException("图标提取功能尚未完全实现");
    }

    class CheckResult
    {
        public string AssemblyPath { get; set; } = "";
        public bool Exists { get; set; }
        public bool IsNetAssembly { get; set; }
        public bool HasEntryPoint { get; set; }
        public string? EntryPointToken { get; set; }
        public string? EntryPointMethod { get; set; }
        public string? AssemblyName { get; set; }
        public string? AssemblyVersion { get; set; }
        public bool HasIcon { get; set; }
        public bool IconExtracted { get; set; }
        public string? IconPath { get; set; }
        public string? IconExtractionError { get; set; }
        public string? Error { get; set; }
    }
}
