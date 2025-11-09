
using HarmonyLib;
using System;
using System.Collections;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Reflection.Metadata;
using System.Runtime.CompilerServices;
using System.Runtime.InteropServices;
using System.Runtime.Loader;
using System.Threading;
using MonoMod.Logs;
// using Terraria.ModLoader.Core; // 移除硬引用，改用反射动态加载

namespace AssemblyMain
{
    public static class Program
    {
        private static readonly Dictionary<string, nint> assemblies = new Dictionary<string, nint>();

        private static readonly ConcurrentDictionary<string, Assembly> _resolvedAssemblies = new();

        private static readonly ConcurrentDictionary<string, string> _assemblyPathCache = new();


        // Native层的coreclr_execute_assembly包装函数指针
        private static IntPtr _executeAssemblyCallbackPtr = IntPtr.Zero;
        
        // 内部使用的委托类型（用于调用Native回调）
        [UnmanagedFunctionPointer(CallingConvention.Cdecl)]
        private delegate int ExecuteAssemblyDelegate(string assemblyPath);

        /// <summary>
        /// 设置Native层的executeAssembly回调函数（使用UnmanagedCallersOnly）
        /// </summary>
        [UnmanagedCallersOnly(CallConvs = new[] { typeof(CallConvCdecl) })]
        public static void SetExecuteAssemblyCallback(IntPtr callbackPtr)
        {
            _executeAssemblyCallbackPtr = callbackPtr;
            Console.WriteLine("[Bootstrap] ExecuteAssembly callback registered at: 0x" + callbackPtr.ToString("X"));
        }

        /// <summary>
        /// Bootstrap启动入口（使用UnmanagedCallersOnly），供Native层通过coreclr_create_delegate调用
        /// </summary>
        /// <param name="targetGamePathPtr">目标游戏程序集路径（UTF-8字符串指针）</param>
        /// <returns>0表示成功，其他值表示错误码</returns>
        [UnmanagedCallersOnly(CallConvs = new[] { typeof(CallConvCdecl) })]
        public static int LaunchGame(IntPtr targetGamePathPtr,IntPtr targetDotnetPtr)
        {
            try
            {
                // 步骤0: 初始化Console重定向到logcat
                DualWriter.Initialize();
                
                // 步骤0.5: 配置 MonoMod 日志输出到 Console（通过 DualWriter 输出到 logcat）
                try
                {
                    // 订阅所有级别的 MonoMod 日志（DefaultFilter 包含 Error, Warning, Info, Trace，不包括 Spam）
                    // 如果需要包含 Spam，可以使用 LogLevelFilter.DefaultFilter | LogLevelFilter.Spam
                    DebugLog.Subscribe(LogLevelFilter.DefaultFilter,
                        (source, time, level, message) =>
                        {
                            // 输出到 Console，DualWriter 会自动转发到 logcat
                            Console.WriteLine($"[MonoMod-{level}] [{source}] {message}");
                        });
                    Console.WriteLine("[Bootstrap] ✓ MonoMod log subscription configured (Error/Warning/Info/Trace)");
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[Bootstrap] ⚠ Failed to configure MonoMod logging: {ex.Message}");
                    // 不返回错误，继续执行
                }
                
                // 步骤1: 解析参数
                string targetGamePath = null;
                string targetDotnet = null;
                
                try
                {
                    if (targetGamePathPtr == IntPtr.Zero)
                    {
                        Console.WriteLine("[Bootstrap] ERROR: targetGamePathPtr is null");
                        return -2;
                    }
                    
                    targetGamePath = Marshal.PtrToStringUTF8(targetGamePathPtr);
                    Console.WriteLine($"[Bootstrap] Target game path: {targetGamePath}");
                    
                    if (targetDotnetPtr != IntPtr.Zero)
                    {
                        targetDotnet = Marshal.PtrToStringUTF8(targetDotnetPtr);
                        if (!string.IsNullOrEmpty(targetDotnet))
                        {
                            Console.WriteLine($"[Bootstrap] Target dotnet path: {targetDotnet}");
                        }
                    }
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[Bootstrap] ERROR: Failed to parse arguments: {ex.Message}");
                    return -3;
                }
                
                // 步骤2: ⚠️ 配置混合运行时（CoreCLR + 静态链接的 Mono JIT）
                // 
                // 关键特性：
                // - libcoreclr.so 内部静态链接了 Mono JIT (libmonosgen-2.0.a)
                // - 暴露标准 CoreCLR API（coreclr_initialize, coreclr_create_delegate 等）
                // - JIT 功能通过 getJit() 返回内嵌的 Mono JIT 实例
                // - 但也暴露了 Mono.Runtime 类型，导致 MonoMod 误检测
                //
                Console.WriteLine("[Bootstrap] Configuring hybrid runtime detection...");
                
                try
                {
                   
                    
                    // 2.2 设置 JIT 路径为 libcoreclr.so（因为 Mono JIT 静态链接在其中）
                    if (!string.IsNullOrEmpty(targetDotnet))
                    {
                        string coreclrPath = Path.Combine(targetDotnet, "shared", "Microsoft.NETCore.App", "8.0.0", "libcoreclr.so");
                        if (!File.Exists(coreclrPath))
                        {
                            coreclrPath = Path.Combine(targetDotnet, "libcoreclr.so");
                        }
                        
                        if (File.Exists(coreclrPath))
                        {
                            var coreBaseRuntimeType = Type.GetType("MonoMod.Core.Platforms.Runtimes.CoreBaseRuntime, MonoMod.Core");
                            if (coreBaseRuntimeType != null)
                            {
                                var setJitPathMethod = coreBaseRuntimeType.GetMethod("SetManuallyLoadedJitPath",
                                    BindingFlags.Public | BindingFlags.Static);
                                if (setJitPathMethod != null)
                                {
                                    setJitPathMethod.Invoke(null, new object[] { coreclrPath });
                                    Console.WriteLine($"[Bootstrap] ✓ Set JIT path: {coreclrPath}");
                                    Console.WriteLine("[Bootstrap]   (contains statically-linked Mono JIT)");
                                }
                            }
                        }
                    }
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[Bootstrap] ⚠ Hybrid runtime config failed: {ex.Message}");
                }
                
                if (string.IsNullOrEmpty(targetGamePath))
                {
                    Console.WriteLine("[Bootstrap] ERROR: Target game path is empty");
                    return -4; // 字符串为空
                }
                
                // 步骤3: 检查文件是否存在
                if (!File.Exists(targetGamePath))
                {
                    Console.WriteLine($"[Bootstrap] ERROR: File not found: {targetGamePath}");
                    return -5; // 文件不存在
                }
                
                // 步骤4: 检查回调是否设置
                if (_executeAssemblyCallbackPtr == IntPtr.Zero)
                {
                    Console.WriteLine("[Bootstrap] ERROR: Execute assembly callback not set");
                    return -6; // 回调未设置
                }
                
                Console.WriteLine("[Bootstrap] All pre-checks passed, starting initialization...");
                
                // 声明targetAssembly在外层作用域，以便在步骤6中使用
                Assembly targetAssembly = null;
                
                // 步骤5: 执行初始化（细化错误码）
                try
                {
                    string directoryName = Path.GetDirectoryName(targetGamePath);
                    if (string.IsNullOrEmpty(directoryName))
                    {
                        Console.WriteLine("[Bootstrap] ERROR: Cannot get directory name");
                        return -71;
                    }
                    
                    if (!Directory.Exists(directoryName))
                    {
                        Console.WriteLine($"[Bootstrap] ERROR: Directory not exist: {directoryName}");
                        return -72;
                    }
                    
                    try
                    {
            Directory.SetCurrentDirectory(directoryName);
                        Console.WriteLine($"[Bootstrap] Working directory set to: {directoryName}");
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Bootstrap] ERROR: Cannot set working directory: {ex.Message}");
                        return -73;
                    }

                    try
                    {



                        AssemblyLoadContext.Default.Resolving += ResolveManagedAssemblies;






                        // 🔧 注册 UnmanagedDll 解析器（必须在加载任何程序集之前！）
                        AssemblyLoadContext.Default.ResolvingUnmanagedDll += SdlAndroidPatch.ResolveUnmanagedDll;

                        Thread.CurrentThread.Name = "Entry Thread";

                        // 设置FNA/MonoGame环境变量
                        Environment.SetEnvironmentVariable("FNA_WORKAROUND_WINDOW_RESIZABLE", "1");
                        // 强制FNA使用SDL Scancode而不是Keycode（虚拟控制需要）
                        Environment.SetEnvironmentVariable("FNA_KEYBOARD_USE_SCANCODES", "1");
                        // 强制横屏方向（覆盖FNA默认的"LandscapeLeft LandscapeRight Portrait"）
                        Environment.SetEnvironmentVariable("SDL_ORIENTATIONS", "LandscapeLeft LandscapeRight");

                        // 🔧 Android OpenGL 配置：使用 gl4es 转换层
                        // gl4es 将 desktop OpenGL 调用转换为 OpenGL ES（Android 原生支持）
                        Environment.SetEnvironmentVariable("FNA3D_OPENGL_DRIVER", "gl4es");
                        Environment.SetEnvironmentVariable("LIBGL_ES", "2"); // gl4es: 使用 OpenGL ES 2.0

                        // 🔧 Android 文件系统重定向：避免访问受限路径 (/data/.config 等)
                        // 这些环境变量也在 native 代码中设置，这里是双重保险
                        string gameDir = Directory.GetCurrentDirectory();
                        Environment.SetEnvironmentVariable("HOME", gameDir);
                        Environment.SetEnvironmentVariable("XDG_CONFIG_HOME", Path.Combine(gameDir, ".config"));
                        Environment.SetEnvironmentVariable("XDG_DATA_HOME", Path.Combine(gameDir, ".local", "share"));
                        Environment.SetEnvironmentVariable("XDG_CACHE_HOME", Path.Combine(gameDir, ".cache"));
                        Environment.SetEnvironmentVariable("TMPDIR", Path.Combine(gameDir, "tmp"));

                        Console.WriteLine("[Bootstrap] Environment configured: gl4es + file system redirection");
                        Console.WriteLine($"[Bootstrap] HOME={gameDir}");


                    }



                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Bootstrap] ERROR: Basic environment setup failed: {ex.Message}");
                        return -74;
                    }
                    
                    try
                    {
                        // 预加载Bootstrap目录中的修复版MonoMod
                        string bootstrapDir = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);
                        if (!string.IsNullOrEmpty(bootstrapDir) && Directory.Exists(bootstrapDir))
                        {
                            string[] criticalAssemblies = new[]
                            {
                                "MonoMod.Core.dll",
                                "MonoMod.Utils.dll",
                                "MonoMod.RuntimeDetour.dll",
                                "0Harmony.dll"
                            };
                            
                            foreach (var dllName in criticalAssemblies)
                            {
                                string dllPath = Path.Combine(bootstrapDir, dllName);
                                if (File.Exists(dllPath))
                                {
                                    try
                                    {
                                        Assembly.LoadFrom(dllPath);
                                    }
                                    catch { }
                                }
                            }
                        }
                        
                        _assemblyPathCache.Clear();
                        AddAssembliesToCache(directoryName);
                        
                        string librariesPath = Path.Combine(directoryName, "Libraries");
                        if (Directory.Exists(librariesPath))
                        {
                            AddAssembliesToCache(librariesPath, true);
                        }
                        
                        if (!string.IsNullOrEmpty(bootstrapDir) && Directory.Exists(bootstrapDir))
                        {
                            AddAssembliesToCache(bootstrapDir, true, forceOverride: true);
                        }
                        
                        AppDomain.CurrentDomain.AssemblyResolve += CurrentDomain_AssemblyResolve;
                        Console.WriteLine($"[Bootstrap] Assembly cache ready ({_assemblyPathCache.Count} entries)");
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Bootstrap] Assembly cache failed: {ex.Message}");
                        return -75;
                    }
                    
                    // 加载目标游戏程序集（这样补丁才能找到要patch的类型）
                    try
                    {
                        Console.WriteLine($"[Bootstrap] Loading target assembly: {Path.GetFileName(targetGamePath)}");
                        targetAssembly = Assembly.LoadFrom(targetGamePath);
                        Console.WriteLine($"[Bootstrap] Target assembly loaded successfully");
                        
                        // ⚠️ 关键：在应用补丁之前，强制初始化 MonoMod PlatformTriple
                        // 这样可以提前捕获和诊断任何运行时检测问题
                        Console.WriteLine("[Bootstrap] Initializing MonoMod PlatformTriple...");
                        try
                        {
                            var platformTripleType = Type.GetType("MonoMod.Core.Platforms.PlatformTriple, MonoMod.Core");
                            if (platformTripleType != null)
                            {
                                var currentProperty = platformTripleType.GetProperty("Current", BindingFlags.Public | BindingFlags.Static);
                                if (currentProperty != null)
                                {
                                    var triple = currentProperty.GetValue(null);
                                    Console.WriteLine($"[Bootstrap] ✓ PlatformTriple initialized: {triple}");
                                }
                            }
                        }
                        catch (Exception initEx)
                        {
                            Console.WriteLine($"[Bootstrap] ❌ ERROR: PlatformTriple initialization failed!");
                            Console.WriteLine($"[Bootstrap]   Exception: {initEx.Message}");
                            Console.WriteLine($"[Bootstrap]   Type: {initEx.GetType().Name}");
                            Console.WriteLine($"[Bootstrap]   Full exception: {initEx}");
                            
                            // 递归输出所有内部异常
                            Exception? current = initEx;
                            int depth = 0;
                            while (current != null && depth < 10)
                            {
                                if (current.InnerException != null)
                                {
                                    Console.WriteLine($"[Bootstrap]   Inner Exception [{depth}]: {current.InnerException.GetType().Name}: {current.InnerException.Message}");
                                    Console.WriteLine($"[Bootstrap]   Inner Stack [{depth}]: {current.InnerException.StackTrace}");
                                    current = current.InnerException;
                                    depth++;
                                }
                                else
                                {
                                    break;
                                }
                            }
                            
                            Console.WriteLine($"[Bootstrap]   Stack trace:");
                            Console.WriteLine(initEx.StackTrace);
                            
                            // 这个错误是致命的，无法继续
                            return -90;
                        }
                        
                        // 🔧 立即应用补丁（MonoGame.Framework 现在已加载）
                        try
                        {
                            FuncLoaderPatch.Apply();
                            MouseInputPatch.Apply(); // 修复触屏偏移问题
                        }
                        catch (Exception patchEx)
                        {
                            Console.WriteLine($"[Bootstrap] Patch warning: {patchEx.Message}");
                        }
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Bootstrap] ERROR: Cannot load target assembly: {ex.Message}");
                        return -76;
                    }
                    
                    // 检测游戏类型并决定需要哪些补丁
                    GameDetector.GameInfo gameInfo = null;
                    try
                    {
                        gameInfo = GameDetector.DetectGame(targetGamePath, targetAssembly);
                        GameDetector.PrintGameInfo(gameInfo);
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Bootstrap] Game detection failed: {ex.Message}");
                        gameInfo = new GameDetector.GameInfo
                        {
                            RequiresFileCasingsPatch = true
                        };
                    }
                    
                    // GetEntryAssembly补丁
                    try
                    {
                        GetEntryAssembly.GetEntryAssemblyPatch();
                        Console.WriteLine("[Bootstrap] GetEntryAssembly patch applied");
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Bootstrap] GetEntryAssembly patch failed: {ex.Message}");
                    }
                    
                    // LoggingHooks补丁
                    try
                    {
                        LoggingHooksPatch.LoggingHooksHarmonyPatch(targetGamePath);
                        Console.WriteLine("[Bootstrap] LoggingHooks patch applied");
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Bootstrap] LoggingHooks patch failed: {ex.Message}");
                    }
                    
                    // TryFixFileCasings补丁
                    if (gameInfo != null && gameInfo.RequiresFileCasingsPatch)
                    {
                        try
                        {
                            TryFixFileCasings.TryFixFileCasingsPatch(targetGamePath);
                            Console.WriteLine("[Bootstrap] FileCasings patch applied");
                        }
                        catch (Exception ex)
                        {
                            Console.WriteLine($"[Bootstrap] FileCasings patch failed: {ex.Message}");
                        }
                    }
                    
                    // Terraria全屏补丁
                    if (gameInfo != null && gameInfo.RequiresFullscreenPatch)
                    {
                        try
                        {
                            TerrariaFullscreenPatch.ApplyFullscreenPatch(targetGamePath);
                            Console.WriteLine("[Bootstrap] Fullscreen patch applied");
                        }
                        catch (Exception ex)
                        {
                            Console.WriteLine($"[Bootstrap] Fullscreen patch failed: {ex.Message}");
                        }
                    }
                    
                    // 性能监控和FPS钩子
                    try
                    {
                        PerformanceMonitor.Start();
                        FnaPerformanceHook.ApplyPatch();
                        Console.WriteLine("[Bootstrap] Performance monitoring enabled");
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Bootstrap] Performance monitoring failed: {ex.Message}");
                    }
                    
                    Console.WriteLine("[Bootstrap] ✅ Initialization completed successfully!");
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[Bootstrap] ERROR: Initialization failed: {ex.Message}");
                    Console.WriteLine($"[Bootstrap] Stack trace: {ex.StackTrace}");
                    return -7;
                }
                
                // 步骤6: 通过反射直接调用游戏入口点（不使用native callback以避免Mono冲突）
                try
                {
                    Console.WriteLine("[Bootstrap] �� Launching game via reflection...");
                    Console.WriteLine($"[Bootstrap] Target assembly: {targetAssembly.FullName}");
                    
                    // 获取入口点
                    MethodInfo entryPoint = targetAssembly.EntryPoint;
                    if (entryPoint == null)
                    {
                        Console.WriteLine("[Bootstrap] ERROR: No entry point found in target assembly");
                        return -81;
                    }
                    
                    Console.WriteLine($"[Bootstrap] Entry point: {entryPoint.DeclaringType.FullName}.{entryPoint.Name}");
                    
                    // 准备参数（空字符串数组）
                    object[] args = new object[] { new string[0] };
                    
                    // 调用入口点
                    Console.WriteLine("[Bootstrap] �� Invoking game entry point...");
                    object result = entryPoint.Invoke(null, args);
                    
                    // 返回结果
                    int exitCode = result is int code ? code : 0;
                    Console.WriteLine($"[Bootstrap] ✅ Game execution finished with exit code: {exitCode}");
                    return exitCode;
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[Bootstrap] ERROR: Game execution failed: {ex.Message}");
                    Console.WriteLine($"[Bootstrap] Stack trace: {ex.StackTrace}");
                    if (ex.InnerException != null)
                    {
                        Console.WriteLine($"[Bootstrap] Inner exception: {ex.InnerException.Message}");
                        Console.WriteLine($"[Bootstrap] Inner stack trace: {ex.InnerException.StackTrace}");
                    }
                    throw;
                    return -8;
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[Bootstrap] FATAL ERROR: {ex.Message}");
                Console.WriteLine($"[Bootstrap] Stack trace: {ex.StackTrace}");
                return -1;
                throw;
            }
        }

       
     

        public static Assembly CurrentDomain_AssemblyResolve(object sender, ResolveEventArgs args)
        {
            try
            {
                // 检查已解析缓存
                if (_resolvedAssemblies.TryGetValue(args.Name, out var cached))
                    return cached;

                var requestedName = new AssemblyName(args.Name);
                string simpleName = requestedName.Name;
                Console.WriteLine($"Resolving assembly: {simpleName}");

                // 从缓存中获取程序集路径
                if (_assemblyPathCache.TryGetValue(simpleName, out string assemblyPath))
                {
                    var assembly = LoadAssemblyWithDependencies(assemblyPath);
                    _resolvedAssemblies[args.Name] = assembly;
                    return assembly;
                }

                Console.WriteLine($"Assembly not found in cache: {simpleName}");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Assembly resolve failed for {args.Name}: {ex}");
            }

            return null;
        }
        private static Assembly LoadAssemblyWithDependencies(string path)
        {
            Console.WriteLine($"Loading assembly: {path}");
            var assembly = Assembly.LoadFrom(path);

            // 预加载所有依赖项
            foreach (var refAsm in assembly.GetReferencedAssemblies())
            {
                if (_resolvedAssemblies.ContainsKey(refAsm.FullName))
                    continue;

                try
                {
                    // 触发解析
                    Assembly.Load(refAsm);
                }
                catch
                {
                    Console.WriteLine($"Warning: Could not preload dependency {refAsm.Name}");
                }
            }

            return assembly;
        }



    


        private static void AddAssembliesToCache(string path, bool recursive = false, bool forceOverride = false, bool applyFilter = true)
        {
            var searchOption = recursive ? SearchOption.AllDirectories : SearchOption.TopDirectoryOnly;
            try
            {
                foreach (var dllPath in Directory.GetFiles(path, "*.dll", searchOption))
                {
                    string fileNameWithoutExtension = Path.GetFileNameWithoutExtension(dllPath);

                  
                    // 如果forceOverride为true，强制覆盖已存在的条目（用于Bootstrap目录）
                    if (forceOverride)
                    {
                        _assemblyPathCache[fileNameWithoutExtension] = dllPath;
                    }
                    else
                    {
                    _assemblyPathCache.TryAdd(fileNameWithoutExtension, dllPath);
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error building assembly cache from {path}: {ex}");
            }
        }

        private static Assembly ResolveManagedAssemblies(AssemblyLoadContext ctx, AssemblyName name)
        {
            if (name.Name is null)
                return null;

            try
            {
            // 🔧 动态查找 AssemblyManager 类型（tModLoader特有）
            // 只在 tModLoader 游戏中才存在，其他游戏中不会加载
            Type assemblyManagerType = null;
            
            // 尝试从已加载的程序集中查找 AssemblyManager
            foreach (var loadedAssembly in AppDomain.CurrentDomain.GetAssemblies())
            {
                try
                {
                    assemblyManagerType = loadedAssembly.GetType("Terraria.ModLoader.Core.AssemblyManager");
                    if (assemblyManagerType != null)
                        break;
                }
                catch { }
            }
            
            // 如果找不到 AssemblyManager（非 tModLoader 游戏），直接返回
            if (assemblyManagerType == null)
            {
                // 回退到简单的程序集缓存查找
                if (_assemblyPathCache.TryGetValue(name.Name, out string assemblyPath))
                {
                    if (File.Exists(assemblyPath))
                    {
                        return Assembly.LoadFrom(assemblyPath);
                    }
                }
                return null;
            }
            
            // 以下是 tModLoader 特有的解析逻辑
            FieldInfo loadedModContextsField = assemblyManagerType.GetField("loadedModContexts",
                BindingFlags.NonPublic | BindingFlags.Static);

            if (loadedModContextsField == null)
            {
                return null;
            }

            // 使用非泛型方式处理字典
            var dict = loadedModContextsField.GetValue(null) as IDictionary;
            if (dict == null)
            {
                return null;
            }

            // 获取 ModLoadContext 类型
            Type modLoadContextType = assemblyManagerType.GetNestedType("ModLoadContext",
                BindingFlags.NonPublic);

            if (modLoadContextType == null)
            {
                return null;
            }

            // 获取 assemblies 字段
            FieldInfo assembliesField = modLoadContextType.GetField("assemblies",
                BindingFlags.Public | BindingFlags.Instance);

            if (assembliesField == null)
            {
                return null;
            }

            // 遍历所有加载的 ModLoadContext
            foreach (var key in dict.Keys)
            {
                object modLoadContext = dict[key];
                var assemblies = assembliesField.GetValue(modLoadContext) as Dictionary<string, Assembly>;

                if (assemblies != null && assemblies.TryGetValue(name.Name, out Assembly asm))
                {
                    return asm;
                }
            }

            return null;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"ResolveManagedAssemblies error: {ex.Message}");
            
            // 异常时回退到简单的程序集缓存查找
            if (_assemblyPathCache.TryGetValue(name.Name, out string assemblyPath))
            {
                if (File.Exists(assemblyPath))
                {
                    try
                    {
                        return Assembly.LoadFrom(assemblyPath);
                    }
                    catch { }
                }
            }
            return null;
        }
        }
    }
}
