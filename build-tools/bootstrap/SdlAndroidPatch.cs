using System;
using System.Reflection;
using System.Runtime.InteropServices;

namespace AssemblyMain
{
    /// <summary>
    /// 修复 MonoGame 在 Android 上的 Native 库加载问题
    /// 通过 AssemblyLoadContext.ResolvingUnmanagedDll 事件拦截库加载请求
    /// </summary>
    public static class SdlAndroidPatch
    {
        private static bool _isAndroid = false;
        private static bool _isAndroidChecked = false;
        
        /// <summary>
        /// UnmanagedDll 解析事件处理器（在加载任何程序集之前注册）
        /// </summary>
        public static IntPtr ResolveUnmanagedDll(Assembly assembly, string unmanagedDllName)
        {
            try
            {
                // 延迟检测 Android 平台（只检测一次）
                if (!_isAndroidChecked)
                {
                    _isAndroid = IsAndroid();
                    _isAndroidChecked = true;
                    
                    if (_isAndroid)
                    {
                        Console.WriteLine("[SdlAndroidPatch] Detected Android platform, Native library resolver active");
                    }
                }
                
                // 只在 Android 上处理
                if (!_isAndroid)
                {
                    return IntPtr.Zero;
                }
                
                // 🔧 修复 libSDL2-2.0.so.0 (Linux) -> libSDL2.so (Android)
                if (unmanagedDllName == "libSDL2-2.0.so.0" || 
                    unmanagedDllName == "libSDL2.so.2" ||
                    unmanagedDllName == "libSDL2-2.0.0.dylib")
                {
                    Console.WriteLine($"[SdlAndroidPatch] Redirecting {unmanagedDllName} -> libSDL2.so");
                    
                    if (NativeLibrary.TryLoad("libSDL2.so", assembly, null, out IntPtr handle))
                    {
                        Console.WriteLine($"[SdlAndroidPatch] ✓ Successfully loaded libSDL2.so (handle: 0x{handle:X})");
                        return handle;
                    }
                    else
                    {
                        Console.WriteLine($"[SdlAndroidPatch] ⚠ Failed to load libSDL2.so");
                    }
                }
                
                // 🔧 修复 libdl.so.2 (Linux) -> libdl.so (Android)
                else if (unmanagedDllName == "libdl.so.2" || unmanagedDllName == "dl")
                {
                    Console.WriteLine($"[SdlAndroidPatch] Redirecting {unmanagedDllName} -> libdl.so");
                    
                    if (NativeLibrary.TryLoad("libdl.so", assembly, null, out IntPtr handle))
                    {
                        Console.WriteLine($"[SdlAndroidPatch] ✓ Successfully loaded libdl.so (handle: 0x{handle:X})");
                        return handle;
                    }
                    else
                    {
                        Console.WriteLine($"[SdlAndroidPatch] ⚠ Failed to load libdl.so");
                    }
                }
                
                // 🔧 修复其他版本化的 .so 文件
                else if (unmanagedDllName.StartsWith("lib") && unmanagedDllName.Contains(".so."))
                {
                    // 提取基础库名：libXXX.so.N -> libXXX.so
                    string baseName = unmanagedDllName.Substring(0, unmanagedDllName.IndexOf(".so.") + 3);
                    Console.WriteLine($"[SdlAndroidPatch] Redirecting versioned library {unmanagedDllName} -> {baseName}");
                    
                    if (NativeLibrary.TryLoad(baseName, assembly, null, out IntPtr handle))
                    {
                        Console.WriteLine($"[SdlAndroidPatch] ✓ Successfully loaded {baseName} (handle: 0x{handle:X})");
                        return handle;
                    }
                }
                
                // 其他库不处理，返回 IntPtr.Zero 让系统使用默认解析
                return IntPtr.Zero;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[SdlAndroidPatch] Error resolving {unmanagedDllName}: {ex.Message}");
                return IntPtr.Zero;
            }
        }
        
        /// <summary>
        /// 检测是否在 Android 上运行
        /// </summary>
        private static bool IsAndroid()
        {
            // 方法1: 检查 Android 特有的环境变量
            try
            {
                string androidRoot = Environment.GetEnvironmentVariable("ANDROID_ROOT");
                string androidData = Environment.GetEnvironmentVariable("ANDROID_DATA");
                
                if (!string.IsNullOrEmpty(androidRoot) || !string.IsNullOrEmpty(androidData))
                {
                    return true;
                }
            }
            catch { }
            
            // 方法2: 检查系统类型（Unix + 特定路径）
            try
            {
                if (Environment.OSVersion.Platform == PlatformID.Unix)
                {
                    // Android 的 uname 返回 "Linux"，但有特定的文件系统结构
                    if (System.IO.Directory.Exists("/system") && 
                        System.IO.Directory.Exists("/data"))
                    {
                        return true;
                    }
                }
            }
            catch { }
            
            return false;
        }
    }
}
