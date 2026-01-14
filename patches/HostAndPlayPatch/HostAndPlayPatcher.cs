using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using HarmonyLib;
using Terraria;
using Terraria.Localization;

namespace HostAndPlayPatch;

/// <summary>
/// Wrapper for UTF-8 string array marshaling with proper memory management
/// </summary>
public sealed class Utf8StringArrayMarshaler : IDisposable
{
    private readonly IntPtr[] _stringPointers;
    private readonly IntPtr _arrayPointer;

    public Utf8StringArrayMarshaler(string[] strings)
    {
        if (strings == null || strings.Length == 0)
        {
            _stringPointers = [];
            _arrayPointer = IntPtr.Zero;
            return;
        }

        _stringPointers = new IntPtr[strings.Length];
        
        // Convert each string to UTF-8 and allocate native memory
        for (int i = 0; i < strings.Length; i++)
        {
            byte[] utf8Bytes = Encoding.UTF8.GetBytes(strings[i] + '\0'); // Null-terminated
            _stringPointers[i] = Marshal.AllocHGlobal(utf8Bytes.Length);
            Marshal.Copy(utf8Bytes, 0, _stringPointers[i], utf8Bytes.Length);
        }

        // Allocate array of pointers (char**)
        _arrayPointer = Marshal.AllocHGlobal(IntPtr.Size * _stringPointers.Length);
        Marshal.Copy(_stringPointers, 0, _arrayPointer, _stringPointers.Length);
    }

    public IntPtr ArrayPointer => _arrayPointer;

    public void Dispose()
    {
        // Free each individual string
        foreach (var ptr in _stringPointers)
        {
            if (ptr != IntPtr.Zero)
            {
                Marshal.FreeHGlobal(ptr);
            }
        }

        // Free the pointer array
        if (_arrayPointer != IntPtr.Zero)
        {
            Marshal.FreeHGlobal(_arrayPointer);
        }
    }
}

public static class HostAndPlayPatcher
{
    private static Harmony? _harmony;
    
    // P/Invoke 声明 - 启动进程
    // 参数: assemblyPath, argc, argv (char**), title, gameId
    [DllImport("main", EntryPoint = "game_launcher_launch_new_dotnet_process", CallingConvention = CallingConvention.Cdecl)]
    private static extern int NativeProcessLauncherStart(
        [MarshalAs(UnmanagedType.LPUTF8Str)] string assemblyPath,
        int argc,
        IntPtr argv, // char** - manually marshaled UTF-8 string array
        [MarshalAs(UnmanagedType.LPUTF8Str)] string title,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string gameId);
    
    public static int Initialize()
    {
        try
        {
            Console.WriteLine("========================================");
            Console.WriteLine("[HostAndPlayPatch] Android Multi-Process Host & Play");
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
            var args = new List<string>
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
            
            Console.WriteLine($"[HostAndPlayPatch] Assembly: {gamePath}");
            Console.WriteLine($"[HostAndPlayPatch] Args: {string.Join(" ", args)}");
            
            // 使用 UTF-8 字符串数组包装器调用 native 进程启动器，确保内存被正确释放
            using (var marshaler = new Utf8StringArrayMarshaler(args.ToArray()))
            {
                var result = NativeProcessLauncherStart(
                    gamePath, 
                    args.Count, 
                    marshaler.ArrayPointer, 
                    "tModLoader Server", 
                    "tModLoader");

                if (result != 0)
                {
                    Console.WriteLine($"[HostAndPlayPatch] Failed to start server: {result}");
                    return false;
                }
            } // marshaler.Dispose() 自动调用，释放所有分配的内存

            Console.WriteLine("[HostAndPlayPatch] Server process started successfully");
            return true;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[HostAndPlayPatch] Exception: {ex.Message}");
            Console.WriteLine($"[HostAndPlayPatch] Stack: {ex.StackTrace}");
            return false;
        }
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
            string gamePath = Path.Combine(
                Path.GetDirectoryName(typeof(Main).Assembly.Location) ?? "",
                "tModLoader.dll");
            
            Console.WriteLine($"[HostAndPlayPatch] Game: {gamePath}");
            Console.WriteLine($"[HostAndPlayPatch] World: {worldPath}");
            
            // 验证文件存在
            if (!File.Exists(worldPath))
            {
                Console.WriteLine($"[HostAndPlayPatch] ERROR: World file not found!");
                Main.menuMode = 0;
                return false;
            }
            
            // 启动服务器进程
            var success = HostAndPlayPatcher.StartServerProcess(
                gamePath,
                worldPath,
                Netplay.ServerPassword, 
                Main.maxNetPlayers);
            
            if (!success)
            {
                Console.WriteLine("[HostAndPlayPatch] Failed to start server!");
                Main.menuMode = 0;
                return false;
            }
            
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
