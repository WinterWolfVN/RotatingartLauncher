using System;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Collections.Generic;
using HarmonyLib;

/// <summary>
/// DOTNET_STARTUP_HOOKS 入口类
/// 此类必须在全局命名空间（没有命名空间）
/// 会在应用程序 Main() 方法之前自动执行
/// </summary>
internal class StartupHook
{
    /// <summary>
    /// DOTNET_STARTUP_HOOKS 要求的初始化方法
    /// 无参数,返回 void
    /// </summary>
    public static void Initialize()
    {
        Console.WriteLine("[ServerStartupHook] DOTNET_STARTUP_HOOKS executing...");

        // 注册程序集解析器,从补丁 DLL 同目录加载依赖
        AppDomain.CurrentDomain.AssemblyResolve += OnAssemblyResolve;

        // 调用原有的补丁初始化方法
        int result = TMLServerPatch.ServerPatcher.Initialize(IntPtr.Zero, 0);
        Console.WriteLine($"[ServerStartupHook] ServerPatcher.Initialize returned: {result}");
    }

    /// <summary>
    /// 程序集解析器 - 从 patches 根目录加载共享依赖程序集
    /// </summary>
    private static Assembly? OnAssemblyResolve(object? sender, ResolveEventArgs args)
    {
        try
        {
            // 获取请求的程序集名称(不含版本等信息)
            string assemblyName = new AssemblyName(args.Name).Name ?? "";

            // 获取当前补丁 DLL 所在目录
            string patchDir = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location) ?? "";

            // 获取 patches 根目录 (补丁 DLL 的父目录)
            string patchesRootDir = Path.GetDirectoryName(patchDir) ?? "";

            // 优先从 patches 根目录加载共享依赖
            string sharedAssemblyPath = Path.Combine(patchesRootDir, assemblyName + ".dll");
            if (File.Exists(sharedAssemblyPath))
            {
                Console.WriteLine($"[ServerStartupHook] Loading shared dependency: {assemblyName} from {sharedAssemblyPath}");
                return Assembly.LoadFrom(sharedAssemblyPath);
            }

            // 备选: 从补丁自己的目录加载
            string localAssemblyPath = Path.Combine(patchDir, assemblyName + ".dll");
            if (File.Exists(localAssemblyPath))
            {
                Console.WriteLine($"[ServerStartupHook] Loading local dependency: {assemblyName} from {localAssemblyPath}");
                return Assembly.LoadFrom(localAssemblyPath);
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ServerStartupHook] Failed to resolve assembly {args.Name}: {ex.Message}");
        }

        return null;
    }
}

namespace TMLServerPatch
{
    /// <summary>
    /// tModLoader 服务器模式补丁
    /// 启用专用服务器模式并修复 Android 平台兼容性问题
    /// </summary>
    public class ServerPatcher
    {
        private static Harmony? _harmony;

        /// <summary>
        /// 补丁初始化方法
        /// 会在游戏程序集加载前被自动调用
        /// </summary>
        public static int Initialize(IntPtr arg, int argSize)
        {
            try
            {
                Console.WriteLine("========================================");
                Console.WriteLine("[TMLServerPatch] Initializing server mode patch...");
                Console.WriteLine("========================================");

                // 打印补丁信息
                PrintPatchInfo();

                // 应用 Harmony 补丁
                ApplyHarmonyPatches();

                Console.WriteLine("[TMLServerPatch] Patch initialized successfully");
                Console.WriteLine("========================================");

                return 0; // 成功
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[TMLServerPatch] ERROR: {ex.Message}");
                Console.WriteLine($"[TMLServerPatch] Stack: {ex.StackTrace}");
                return -1; // 失败
            }
        }

        /// <summary>
        /// 打印补丁信息
        /// </summary>
        private static void PrintPatchInfo()
        {
            var assembly = Assembly.GetExecutingAssembly();
            var version = assembly.GetName().Version;

            Console.WriteLine($"Patch Assembly: {assembly.GetName().Name}");
            Console.WriteLine($"Version: {version}");
            Console.WriteLine($"Location: {assembly.Location}");
            Console.WriteLine($".NET Version: {Environment.Version}");
            Console.WriteLine($"Harmony Version: {typeof(Harmony).Assembly.GetName().Version}");
        }


        /// <summary>
        /// 应用 Harmony 补丁
        /// </summary>
        private static void ApplyHarmonyPatches()
        {
            try
            {
                _harmony = new Harmony("com.ralaunch.tmlserverpatch");

                // 补丁 Console 方法以避免 PlatformNotSupportedException
                PatchConsoleMethods();

                // Hook "Host & Play" 服务器启动方法
                PatchHostAndPlayMethod();

                // Hook 网络连接以禁止外部服务器连接
                PatchNetworkConnections();

                Console.WriteLine("[TMLServerPatch] Harmony patches applied successfully");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[TMLServerPatch] Failed to apply Harmony patches: {ex.Message}");
                throw;
            }
        }

        /// <summary>
        /// Hook Main.OnSubmitServerPassword 方法以使用 Android 进程启动服务器
        /// </summary>
        private static void PatchHostAndPlayMethod()
        {
            try
            {
                // 等待 tModLoader 程序集被加载
                Assembly? tModLoaderAssembly = null;
                for (int i = 0; i < 100; i++)  // 最多等待10秒
                {
                    tModLoaderAssembly = AppDomain.CurrentDomain.GetAssemblies()
                        .FirstOrDefault(a => a.GetName().Name == "tModLoader");

                    if (tModLoaderAssembly != null)
                        break;

                    System.Threading.Thread.Sleep(100);
                }

                if (tModLoaderAssembly == null)
                {
                    Console.WriteLine("[TMLServerPatch] WARNING: tModLoader assembly not found, skipping Host & Play hook");
                    return;
                }

                Console.WriteLine("[TMLServerPatch] Found tModLoader assembly, hooking Host & Play...");

                // 获取 Main 类型
                var mainType = tModLoaderAssembly.GetType("Terraria.Main");
                if (mainType == null)
                {
                    Console.WriteLine("[TMLServerPatch] WARNING: Terraria.Main type not found");
                    return;
                }

                // 查找 OnSubmitServerPassword 方法（私有实例方法）
                var onSubmitMethod = mainType.GetMethod("OnSubmitServerPassword",
                    BindingFlags.NonPublic | BindingFlags.Instance,
                    null,
                    Type.EmptyTypes,
                    null);

                if (onSubmitMethod != null)
                {
                    var prefix = new HarmonyMethod(typeof(ServerPatcher), nameof(OnSubmitServerPassword_Prefix));
                    _harmony!.Patch(onSubmitMethod, prefix: prefix);
                    Console.WriteLine("[TMLServerPatch] ✓ Hooked Main.OnSubmitServerPassword");
                }
                else
                {
                    Console.WriteLine("[TMLServerPatch] WARNING: OnSubmitServerPassword method not found");
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[TMLServerPatch] Error hooking Host & Play: {ex.Message}");
                Console.WriteLine($"[TMLServerPatch] Stack: {ex.StackTrace}");
            }
        }

        /// <summary>
        /// OnSubmitServerPassword 的 Prefix hook
        /// 在当前进程中启动服务器（类似 Minecraft 的"开启局域网"）
        /// </summary>
        private static bool OnSubmitServerPassword_Prefix(object __instance)
        {
            try
            {
                Console.WriteLine("[TMLServerPatch] OnSubmitServerPassword called - starting local multiplayer server");

                // 获取必要的字段
                var mainType = __instance.GetType();
                var assembly = mainType.Assembly;

                // 获取 Netplay 类型
                var netplayType = assembly.GetType("Terraria.Netplay");
                if (netplayType == null)
                {
                    Console.WriteLine("[TMLServerPatch] ERROR: Netplay type not found");
                    return true;
                }

                // 调用 Netplay.StartServer 在当前进程中启动服务器
                var startServerMethod = netplayType.GetMethod("StartServer", BindingFlags.Public | BindingFlags.Static);
                if (startServerMethod == null)
                {
                    Console.WriteLine("[TMLServerPatch] ERROR: Netplay.StartServer method not found");
                    return true;
                }

                // 先设置客户端连接到本地服务器（在启动服务器之前）
                var setRemoteIPMethod = netplayType.GetMethod("SetRemoteIP", BindingFlags.Public | BindingFlags.Static);
                if (setRemoteIPMethod != null)
                {
                    setRemoteIPMethod.Invoke(null, new object[] { "127.0.0.1" });
                    Console.WriteLine("[TMLServerPatch] Set remote IP to 127.0.0.1");
                }
                else
                {
                    Console.WriteLine("[TMLServerPatch] WARNING: SetRemoteIP method not found");
                }

                // 尝试直接设置 Netplay 的 IP 字段
                var serverIPField = netplayType.GetField("ServerIP", BindingFlags.Public | BindingFlags.Static);
                if (serverIPField != null)
                {
                    serverIPField.SetValue(null, "127.0.0.1");
                    Console.WriteLine("[TMLServerPatch] Set ServerIP field to 127.0.0.1");
                }

                var serverIPStringField = netplayType.GetField("ServerIPText", BindingFlags.Public | BindingFlags.Static);
                if (serverIPStringField != null)
                {
                    serverIPStringField.SetValue(null, "127.0.0.1");
                    Console.WriteLine("[TMLServerPatch] Set ServerIPText field to 127.0.0.1");
                }

                // 启动服务器（在当前进程中）
                Console.WriteLine("[TMLServerPatch] Starting server in current process...");
                startServerMethod.Invoke(null, null);
                Console.WriteLine("[TMLServerPatch] Server started");

                // 等待一小段时间让服务器启动
                System.Threading.Thread.Sleep(500);

                // 再次确保 IP 是 127.0.0.1
                if (setRemoteIPMethod != null)
                {
                    setRemoteIPMethod.Invoke(null, new object[] { "127.0.0.1" });
                    Console.WriteLine("[TMLServerPatch] Re-confirmed remote IP is 127.0.0.1");
                }

                // 启动 TCP 客户端连接
                var startTcpClientMethod = netplayType.GetMethod("StartTcpClient", BindingFlags.Public | BindingFlags.Static);
                if (startTcpClientMethod != null)
                {
                    startTcpClientMethod.Invoke(null, null);
                    Console.WriteLine("[TMLServerPatch] TCP client started");
                }
                else
                {
                    Console.WriteLine("[TMLServerPatch] WARNING: StartTcpClient method not found");
                }

                // 切换到多人游戏模式
                var menuModeField = mainType.GetField("menuMode", BindingFlags.Public | BindingFlags.Static);
                if (menuModeField != null)
                {
                    menuModeField.SetValue(null, 10); // menuMode = 10 (multiplayer)
                    Console.WriteLine("[TMLServerPatch] Switched to multiplayer mode");
                }

                Console.WriteLine("[TMLServerPatch] ✓ Local multiplayer server started successfully (like Minecraft LAN)");

                // 返回 false 阻止原方法执行（我们已经手动处理了）
                return false;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[TMLServerPatch] Error in OnSubmitServerPassword_Prefix: {ex.Message}");
                Console.WriteLine($"[TMLServerPatch] Stack: {ex.StackTrace}");
                return true; // 出错时继续执行原方法
            }
        }

        /// <summary>
        /// Hook 网络连接以禁止外部服务器连接
        /// 只允许本地局域网连接
        /// </summary>
        private static void PatchNetworkConnections()
        {
            try
            {
                Console.WriteLine("[TMLServerPatch] Patching network connections to block external servers...");

                // 这里可以 Hook System.Net.Sockets.Socket 或 HttpClient 来阻止外部连接
                // 暂时留空，如果需要可以添加

                Console.WriteLine("[TMLServerPatch] Network connection patches skipped (not needed for LAN mode)");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[TMLServerPatch] Error patching network connections: {ex.Message}");
            }
        }

        /// <summary>
        /// 补丁 Console 方法以避免平台不支持的异常
        /// </summary>
        private static void PatchConsoleMethods()
        {
            try
            {
                // 获取 Console 类型
                var consoleType = typeof(Console);

                // 获取 set_Title 方法
                var setTitleMethod = consoleType.GetProperty("Title")?.GetSetMethod();
                if (setTitleMethod != null)
                {
                    var prefix = new HarmonyMethod(typeof(ServerPatcher), nameof(Console_SetTitle_Prefix));
                    _harmony!.Patch(setTitleMethod, prefix: prefix);
                    Console.WriteLine("[TMLServerPatch] ✓ Patched Console.set_Title");
                }

                // 获取 set_ForegroundColor 方法
                var setForegroundColorMethod = consoleType.GetProperty("ForegroundColor")?.GetSetMethod();
                if (setForegroundColorMethod != null)
                {
                    var prefix = new HarmonyMethod(typeof(ServerPatcher), nameof(Console_SetForegroundColor_Prefix));
                    _harmony!.Patch(setForegroundColorMethod, prefix: prefix);
                    Console.WriteLine("[TMLServerPatch] ✓ Patched Console.set_ForegroundColor");
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[TMLServerPatch] Error patching Console methods: {ex.Message}");
            }
        }

        /// <summary>
        /// Console.set_Title 的 Prefix 补丁
        /// 跳过设置控制台标题以避免 PlatformNotSupportedException
        /// </summary>
        private static bool Console_SetTitle_Prefix(string value)
        {
            // 在 Android 上跳过设置控制台标题
            // 返回 false 跳过原始方法
            return false;
        }

        /// <summary>
        /// Console.set_ForegroundColor 的 Prefix 补丁
        /// 跳过设置前景色以避免 PlatformNotSupportedException
        /// </summary>
        private static bool Console_SetForegroundColor_Prefix(ConsoleColor value)
        {
            // 在 Android 上跳过设置前景色
            // 返回 false 跳过原始方法
            return false;
        }
    }
}
