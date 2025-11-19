using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection.Metadata;
using System.Reflection.Metadata.Ecma335;
using System.Reflection.PortableExecutable;
using System.Runtime.InteropServices;
using System.Text.Json;

namespace AssemblyChecker
{
    public class Program
    {
        // 用于 ComponentEntryPoint 签名的包装方法
        // 这允许通过 get_function_with_default_signature 调用
        [UnmanagedCallersOnly]
        public static int ComponentEntryPoint(IntPtr args, int sizeBytes)
        {
            // 从环境变量读取参数（由 C++ 层设置）
            string argsJson = Environment.GetEnvironmentVariable("DOTNET_TOOL_ARGS");
            string[] realArgs = Array.Empty<string>();

            if (!string.IsNullOrEmpty(argsJson))
            {
                try
                {
                    realArgs = JsonSerializer.Deserialize<string[]>(argsJson) ?? Array.Empty<string>();
                }
                catch
                {
                    // 如果反序列化失败，尝试作为单个参数
                    realArgs = new[] { argsJson };
                }
            }

            return Main(realArgs);
        }

        public static int Main(string[] args)
        {
            if (args.Length < 1)
            {
                Console.Error.WriteLine("用法: AssemblyChecker <directory_or_assembly_path> [--extract-icon <output_path>]");
                return 2;
            }

            string path = args[0];
            string iconOutputPath = null;

            // 解析选项参数
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
                // 判断路径是目录还是文件
                if (Directory.Exists(path))
                {
                    // 目录：搜索所有 .dll 和 .exe，找到第一个有入口点的
                    CheckResult result = SearchDirectoryForEntryPoint(path, iconOutputPath);
                    Console.WriteLine(JsonSerializer.Serialize(result, new JsonSerializerOptions
                    {
                        WriteIndented = false
                    }));
                    return result.HasEntryPoint ? 0 : 1;
                }
                else if (File.Exists(path))
                {
                    // 文件：直接检测
                    CheckResult result = CheckAssembly(path, iconOutputPath);
                    Console.WriteLine(JsonSerializer.Serialize(result, new JsonSerializerOptions
                    {
                        WriteIndented = false
                    }));
                    return result.HasEntryPoint ? 0 : 1;
                }
                else
                {
                    CheckResult errorResult = new CheckResult
                    {
                        AssemblyPath = path,
                        Error = "路径不存在"
                    };
                    Console.WriteLine(JsonSerializer.Serialize(errorResult, new JsonSerializerOptions
                    {
                        WriteIndented = false
                    }));
                    return 2;
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine(JsonSerializer.Serialize(new CheckResult
                {
                    Error = ex.Message,
                    HasEntryPoint = false
                }));
                return 2;
            }
        }

        /// <summary>
        /// 搜索目录中的所有程序集，找到第一个有入口点的
        /// </summary>
        private static CheckResult SearchDirectoryForEntryPoint(string directory, string iconOutputPath)
        {
            // 获取目录中的所有 .dll 和 .exe 文件（只搜索顶层，不递归）
            var assemblyFiles = Directory.GetFiles(directory, "*.*")
                .Where(f => f.EndsWith(".dll", StringComparison.OrdinalIgnoreCase) ||
                           f.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
                .ToList();

            Console.Error.WriteLine($"在目录 {directory} 中找到 {assemblyFiles.Count} 个程序集文件");

            CheckResult? firstResult = null;

            // 遍历所有程序集，找到第一个有入口点的
            foreach (string assemblyPath in assemblyFiles)
            {
                try
                {
                    CheckResult result = CheckAssembly(assemblyPath, iconOutputPath);

                    // 保存第一个检测结果
                    if (firstResult == null)
                    {
                        firstResult = result;
                    }

                    // 找到有入口点的程序集，立即返回
                    if (result.HasEntryPoint)
                    {
                        Console.Error.WriteLine($"✓ 找到有入口点的程序集: {Path.GetFileName(assemblyPath)}");
                        return result;
                    }
                }
                catch (Exception ex)
                {
                    Console.Error.WriteLine($"✗ 检测 {Path.GetFileName(assemblyPath)} 失败: {ex.Message}");
                }
            }

            // 没有找到有入口点的程序集
            Console.Error.WriteLine("✗ 未找到有入口点的程序集");

            return firstResult ?? new CheckResult
            {
                AssemblyPath = directory,
                Error = "目录中没有找到有效的程序集"
            };
        }

        private static CheckResult CheckAssembly(string assemblyPath, string? iconOutputPath)
        {
            CheckResult result = new CheckResult
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
                using (FileStream stream = File.OpenRead(assemblyPath))
                using (PEReader peReader = new PEReader(stream))
                {
                    if (!peReader.HasMetadata)
                    {
                        result.Error = "不是有效的 .NET 程序集";
                        return result;
                    }

                    MetadataReader metadataReader = peReader.GetMetadataReader();
                    result.IsNetAssembly = true;

                    CorHeader corHeader = peReader.PEHeaders.CorHeader;
                    if (corHeader == null)
                    {
                        result.Error = "不是有效的 .NET 程序集（缺少 CorHeader）";
                        return result;
                    }

                    int entryPointToken = corHeader.EntryPointTokenOrRelativeVirtualAddress;
                    EntityHandle entryPoint = MetadataTokens.EntityHandle(entryPointToken);

                    result.HasEntryPoint = !entryPoint.IsNil && entryPointToken != 0;

                    if (result.HasEntryPoint)
                    {
                        result.EntryPointToken = $"0x{entryPoint.GetHashCode():X8}";

                        try
                        {
                            if (entryPoint.Kind == HandleKind.MethodDefinition)
                            {
                                MethodDefinition methodDef = metadataReader.GetMethodDefinition((MethodDefinitionHandle)entryPoint);
                                TypeDefinition typeDef = metadataReader.GetTypeDefinition(methodDef.GetDeclaringType());

                                string typeName = metadataReader.GetString(typeDef.Name);
                                string methodName = metadataReader.GetString(methodDef.Name);
                                string namespaceName = metadataReader.GetString(typeDef.Namespace);

                                result.EntryPointMethod = $"{namespaceName}.{typeName}::{methodName}";
                            }
                        }
                        catch
                        {
                            // 忽略入口点解析错误
                        }
                    }

                    AssemblyDefinition assemblyDef = metadataReader.GetAssemblyDefinition();
                    result.AssemblyName = metadataReader.GetString(assemblyDef.Name);
                    result.AssemblyVersion = assemblyDef.Version.ToString();

                    result.HasIcon = HasIconResource(peReader);

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
                }
            }
            catch (BadImageFormatException)
            {
                result.Error = "不是有效的 PE 文件或 .NET 程序集";
            }
            catch (Exception ex)
            {
                result.Error = "读取程序集失败: " + ex.Message;
            }

            return result;
        }

        private static bool HasIconResource(PEReader peReader)
        {
            try
            {
                var resourcesDirectory = peReader.PEHeaders.PEHeader?.ResourceTableDirectory;
                return resourcesDirectory != null && resourcesDirectory.Value.Size > 0;
            }
            catch
            {
                return false;
            }
        }

        private static void ExtractIcon(PEReader peReader, string outputPath)
        {
            var resourcesDirectory = peReader.PEHeaders.PEHeader?.ResourceTableDirectory;
            if (resourcesDirectory == null || resourcesDirectory.Value.Size == 0)
            {
                throw new Exception("没有找到图标资源");
            }

            throw new NotImplementedException("图标提取功能尚未完全实现");
        }

        private class CheckResult
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
}
