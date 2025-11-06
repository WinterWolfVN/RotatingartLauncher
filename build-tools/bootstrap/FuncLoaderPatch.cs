using HarmonyLib;
using System;
using System.Reflection;

namespace AssemblyMain
{
    /// <summary>
    /// 修补 MonoGame FuncLoader 的 LoadLibrary 方法
    /// 在调用 native dlopen 之前修改库名
    /// </summary>
    public static class FuncLoaderPatch
    {
        public static void Apply()
        {
            try
            {
                Console.WriteLine("[FuncLoaderPatch] Applying FuncLoader.LoadLibrary patch...");
                
                var harmony = new Harmony("com.ralaunch.funcloader.android");
                
                // 查找 FuncLoader 类
                Type funcLoaderType = Type.GetType("MonoGame.Framework.Utilities.FuncLoader, MonoGame.Framework");
                if (funcLoaderType == null)
                {
                    Console.WriteLine("[FuncLoaderPatch] FuncLoader type not found, skipping patch");
                    return;
                }
                
                // 补丁 LoadLibrary 方法（static）
                MethodInfo loadLibraryMethod = funcLoaderType.GetMethod("LoadLibrary", 
                    BindingFlags.Public | BindingFlags.Static, 
                    null,
                    new Type[] { typeof(string) },
                    null);
                
                if (loadLibraryMethod == null)
                {
                    Console.WriteLine("[FuncLoaderPatch] LoadLibrary method not found, skipping patch");
                    return;
                }
                
                MethodInfo prefixMethod = typeof(FuncLoaderPatch).GetMethod(nameof(LoadLibrary_Prefix), 
                    BindingFlags.Public | BindingFlags.Static);
                
                harmony.Patch(loadLibraryMethod, new HarmonyMethod(prefixMethod));
                
                Console.WriteLine("[FuncLoaderPatch] ✓ FuncLoader.LoadLibrary patch applied");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[FuncLoaderPatch] Failed to apply patch: {ex.Message}");
                Console.WriteLine($"[FuncLoaderPatch] Stack trace: {ex.StackTrace}");
            }
        }
        
        /// <summary>
        /// Harmony Prefix：修改传递给 dlopen 的库名
        /// </summary>
        public static void LoadLibrary_Prefix(ref string libname)
        {
            try
            {
                // 只在 Android 上处理
                if (!IsAndroid())
                {
                    return;
                }
                
                string originalLibname = libname;
                
                // 🔧 修复 libSDL2-2.0.so.0 -> libSDL2.so
                if (libname == "libSDL2-2.0.so.0" || libname.Contains("libSDL2-2.0.so"))
                {
                    libname = "libSDL2.so";
                    Console.WriteLine($"[FuncLoaderPatch] Redirecting {originalLibname} -> {libname}");
                }
                // 修复 libdl.so.2 -> libdl.so
                else if (libname == "libdl.so.2")
                {
                    libname = "libdl.so";
                    Console.WriteLine($"[FuncLoaderPatch] Redirecting {originalLibname} -> {libname}");
                }
                // 通用：移除版本后缀 lib*.so.N -> lib*.so
                else if (libname.StartsWith("lib") && libname.Contains(".so."))
                {
                    int soIndex = libname.IndexOf(".so.");
                    if (soIndex > 0)
                    {
                        libname = libname.Substring(0, soIndex + 3); // +3 for ".so"
                        Console.WriteLine($"[FuncLoaderPatch] Redirecting versioned library {originalLibname} -> {libname}");
                    }
                }
                // 修复带版本号的库名 lib*-N.N.so.N -> lib*.so
                else if (libname.Contains("-") && libname.EndsWith(".so.0"))
                {
                    int dashIndex = libname.IndexOf('-');
                    libname = libname.Substring(0, dashIndex) + ".so";
                    Console.WriteLine($"[FuncLoaderPatch] Redirecting versioned library {originalLibname} -> {libname}");
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[FuncLoaderPatch] Prefix error: {ex.Message}");
            }
        }
        
        /// <summary>
        /// 检测是否在 Android 上运行
        /// </summary>
        private static bool IsAndroid()
        {
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
            
            return false;
        }
    }
}


