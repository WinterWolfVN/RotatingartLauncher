using System;
using System.Linq;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Threading;
using HarmonyLib;
using Terraria;
using Terraria.Localization;

namespace HostAndPlayPatch;

/// <summary>
/// Host & Play 补丁 - Android 多进程方案
/// 
/// 工作原理：
/// 1. 拦截 OnSubmitServerPassword() 方法
/// 2. C# 构建完整的服务器参数（-server -world xxx -maxplayers xxx 等）
/// 3. 通过 P/Invoke 传递参数给 native 层
/// 4. Native 层通过 JNI 调用通用的 ProcessLauncherService
/// 5. 主进程以客户端模式连接 127.0.0.1:7777
/// </summary>
public static class HostAndPlayPatcher
{
    private static Harmony? _harmony;
    
    // P/Invoke 声明 - 启动进程
    // 参数: assemblyPath, argsJson (JSON数组字符串), startupHooks, title
    [DllImport("main", EntryPoint = "process_launcher_start", CallingConvention = CallingConvention.Cdecl)]
    private static extern int NativeProcessLauncherStart(
        [MarshalAs(UnmanagedType.LPUTF8Str)] string assemblyPath,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string argsJson,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string? startupHooks,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string title);
    
    public static int Initialize()
    {
        try
        {
            Console.WriteLine("========================================");
            Console.WriteLine("[HostAndPlayPatch] Android Multi-Process Host & Play");
            Console.WriteLine("========================================");
            Console.WriteLine("[HostAndPlayPatch] C# 控制所有服务器逻辑");
            Console.WriteLine("[HostAndPlayPatch] Java 仅提供通用进程启动");
            Console.WriteLine("========================================");

            ApplyHarmonyPatches();

            return 0;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[HostAndPlayPatch] ERROR: {ex.Message}");
            return -1;
        }
    }

    private static void ApplyHarmonyPatches()
    {
        _harmony = new Harmony("com.ralaunch.hostandplay");
        
        var loadedAssemblies = AppDomain.CurrentDomain.GetAssemblies();
        var tModLoaderAssembly = loadedAssemblies.FirstOrDefault(a => a.GetName().Name == "tModLoader");

        if (tModLoaderAssembly != null)
        {
            ApplyPatchesInternal(tModLoaderAssembly);
        }
        else
        {
            AppDomain.CurrentDomain.AssemblyLoad += OnAssemblyLoaded;
        }
    }

    private static void OnAssemblyLoaded(object? sender, AssemblyLoadEventArgs args)
    {
        if (args.LoadedAssembly.GetName().Name == "tModLoader")
        {
            ApplyPatchesInternal(args.LoadedAssembly);
            AppDomain.CurrentDomain.AssemblyLoad -= OnAssemblyLoaded;
        }
    }

    private static void ApplyPatchesInternal(Assembly assembly)
    {
        var mainType = assembly.GetType("Terraria.Main");
        if (mainType == null)
        {
            Console.WriteLine("[HostAndPlayPatch] ERROR: Terraria.Main not found!");
            return;
        }

        // Hook OnSubmitServerPassword (无参数版本)
        var onSubmitMethod = mainType.GetMethod("OnSubmitServerPassword", 
            BindingFlags.Instance | BindingFlags.NonPublic,
            null, Type.EmptyTypes, null);
        
        if (onSubmitMethod != null)
        {
            _harmony!.Patch(onSubmitMethod, 
                prefix: new HarmonyMethod(typeof(OnSubmitServerPasswordPatch), nameof(OnSubmitServerPasswordPatch.Prefix)));
            Console.WriteLine("[HostAndPlayPatch] Main.OnSubmitServerPassword() patched!");
        }
        else
        {
            Console.WriteLine("[HostAndPlayPatch] WARNING: OnSubmitServerPassword() not found!");
        }
        
        Console.WriteLine("[HostAndPlayPatch] Patch applied!");
    }
    
    /// <summary>
    /// 启动服务器进程
    /// C# 构建完整的命令行参数，传递给通用的进程启动器
    /// </summary>
    public static bool StartServerProcess(string gamePath, string worldPath, string? password, int maxPlayers)
    {
        try
        {
            Console.WriteLine("[HostAndPlayPatch] Building server arguments...");
            
            // 构建完整的服务器命令行参数
            var args = new System.Collections.Generic.List<string>
            {
                "-server",
                "-world", worldPath,
                "-maxplayers", maxPlayers.ToString(),
                "-port", "7777",
                "-autoshutdown"
            };
            
            if (!string.IsNullOrEmpty(password))
            {
                args.Add("-password");
                args.Add(password);
            }
            
            // 转换为 JSON 数组
            string argsJson = "[" + string.Join(",", args.Select(a => "\"" + EscapeJson(a) + "\"")) + "]";
            
            Console.WriteLine($"[HostAndPlayPatch] Assembly: {gamePath}");
            Console.WriteLine($"[HostAndPlayPatch] Args: {string.Join(" ", args)}");
            
            // 调用 native 进程启动器
            int result = NativeProcessLauncherStart(gamePath, argsJson, null, "tModLoader Server");
            
            if (result == 0)
            {
                Console.WriteLine("[HostAndPlayPatch] Server process started successfully");
                return true;
            }
            else
            {
                Console.WriteLine($"[HostAndPlayPatch] Failed to start server: {result}");
                return false;
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[HostAndPlayPatch] Exception: {ex.Message}");
            Console.WriteLine($"[HostAndPlayPatch] Stack: {ex.StackTrace}");
            return false;
        }
    }
    
    private static string EscapeJson(string s)
    {
        return s.Replace("\\", "\\\\").Replace("\"", "\\\"");
    }
}

/// <summary>
/// Hook Main.OnSubmitServerPassword()
/// 启动服务器进程并连接
/// </summary>
[HarmonyPatch]
public static class OnSubmitServerPasswordPatch
{
    public static bool Prefix(Main __instance)
    {
        Console.WriteLine("========================================");
        Console.WriteLine("[HostAndPlayPatch] Host & Play 请求");
        Console.WriteLine("========================================");
        
        try
        {
            // 获取世界路径
            string worldPath;
            if (Main.ActiveWorldFileData != null && !string.IsNullOrEmpty(Main.ActiveWorldFileData.Path))
            {
                worldPath = Main.ActiveWorldFileData.Path;
            }
            else if (!string.IsNullOrEmpty(Main.worldPathName))
            {
                worldPath = Main.worldPathName;
            }
            else
            {
                Console.WriteLine("[HostAndPlayPatch] ERROR: No world selected!");
                Main.menuMode = 0;
                return false;
            }
            
            // 获取游戏程序集路径
            string gamePath = System.IO.Path.Combine(
                System.IO.Path.GetDirectoryName(typeof(Main).Assembly.Location) ?? "",
                "tModLoader.dll");
            
            Console.WriteLine($"[HostAndPlayPatch] Game: {gamePath}");
            Console.WriteLine($"[HostAndPlayPatch] World: {worldPath}");
            
            // 验证文件存在
            if (!System.IO.File.Exists(worldPath))
            {
                Console.WriteLine($"[HostAndPlayPatch] ERROR: World file not found!");
                Main.menuMode = 0;
                return false;
            }
            
            // 启动服务器进程
            bool success = HostAndPlayPatcher.StartServerProcess(
                gamePath, worldPath, 
                string.IsNullOrEmpty(Netplay.ServerPassword) ? null : Netplay.ServerPassword, 
                Main.maxNetPlayers);
            
            if (!success)
            {
                Console.WriteLine("[HostAndPlayPatch] Failed to start server!");
                Main.menuMode = 0;
                return false;
            }
            
            // 等待服务器启动
            Console.WriteLine("[HostAndPlayPatch] Waiting for server (5s)...");
            Thread.Sleep(5000);
            
            // 连接到本地服务器
            Console.WriteLine("[HostAndPlayPatch] Connecting to 127.0.0.1:7777...");
            Main.netMode = 1;
            Netplay.SetRemoteIP("127.0.0.1");
            Main.autoPass = true;
            Main.statusText = Language.GetTextValue("Net.ConnectingTo", "127.0.0.1:7777");
            
            Netplay.StartTcpClient();
            Main.menuMode = 10;
            
            Console.WriteLine("[HostAndPlayPatch] Client connection started");
            Console.WriteLine("========================================");
            
            return false;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[HostAndPlayPatch] ERROR: {ex.Message}");
            Main.menuMode = 0;
            return false;
        }
    }
}
