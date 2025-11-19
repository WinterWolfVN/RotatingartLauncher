using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Reflection.Emit;
using HarmonyLib;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Graphics;
using Microsoft.Xna.Framework.Input;

/// <summary>
/// DOTNET_STARTUP_HOOKS 入口类
/// </summary>
internal class StartupHook
{
    public static void Initialize()
    {
        Console.WriteLine("[ConsoleStartupHook] DOTNET_STARTUP_HOOKS executing...");

        // 注册程序集解析器
        AppDomain.CurrentDomain.AssemblyResolve += OnAssemblyResolve;

        // 调用补丁初始化方法
        int result = TMLConsolePatch.ConsolePatcher.Initialize(IntPtr.Zero, 0);
        Console.WriteLine($"[ConsoleStartupHook] ConsolePatcher.Initialize returned: {result}");
    }

    private static Assembly? OnAssemblyResolve(object? sender, ResolveEventArgs args)
    {
        try
        {
            string assemblyName = new AssemblyName(args.Name).Name ?? "";
            string patchDir = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location) ?? "";
            string patchesRootDir = Path.GetDirectoryName(patchDir) ?? "";

            // 从 patches 根目录加载共享依赖
            string sharedAssemblyPath = Path.Combine(patchesRootDir, assemblyName + ".dll");
            if (File.Exists(sharedAssemblyPath))
            {
                Console.WriteLine($"[ConsoleStartupHook] Loading shared dependency: {assemblyName}");
                return Assembly.LoadFrom(sharedAssemblyPath);
            }

            // 从补丁目录加载
            string localAssemblyPath = Path.Combine(patchDir, assemblyName + ".dll");
            if (File.Exists(localAssemblyPath))
            {
                Console.WriteLine($"[ConsoleStartupHook] Loading local dependency: {assemblyName}");
                return Assembly.LoadFrom(localAssemblyPath);
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsoleStartupHook] Failed to resolve assembly: {ex.Message}");
        }

        return null;
    }
}

namespace TMLConsolePatch
{
    /// <summary>
    /// tModLoader 控制台UI补丁
    /// </summary>
    public class ConsolePatcher
    {
        private static Harmony? _harmony;
        private static Assembly? _tModLoaderAssembly;

        public static int Initialize(IntPtr arg, int argSize)
        {
            try
            {
                Console.WriteLine("========================================");
                Console.WriteLine("[TMLConsolePatch] Initializing console UI patch...");
                Console.WriteLine("========================================");

                PrintPatchInfo();
                PatchConsoleIfLoaded();
                ApplyHarmonyPatches();

                Console.WriteLine("[TMLConsolePatch] Patch initialized successfully");
                Console.WriteLine("========================================");

                return 0;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[TMLConsolePatch] ERROR: {ex.Message}");
                Console.WriteLine($"[TMLConsolePatch] Stack: {ex.StackTrace}");
                return -1;
            }
        }

        private static void PrintPatchInfo()
        {
            var assembly = Assembly.GetExecutingAssembly();
            Console.WriteLine($"Patch Assembly: {assembly.GetName().Name}");
            Console.WriteLine($"Version: {assembly.GetName().Version}");
            Console.WriteLine($".NET Version: {Environment.Version}");
            Console.WriteLine($"Harmony Version: {typeof(Harmony).Assembly.GetName().Version}");
        }

        private static void PatchConsoleIfLoaded()
        {
            // 不要在补丁加载时访问 tModLoader,因为会触发 Main 的静态构造函数
            // 此时 Program.SavePath 还未初始化,会导致 NullReferenceException
            Console.WriteLine("[TMLConsolePatch] Waiting for tModLoader to load via AssemblyLoad event");
        }

        private static void ApplyHarmonyPatches()
        {
            try
            {
                _harmony = new Harmony("com.ralaunch.tmlconsolepatch");

                // Patch Console.WriteLine
                PatchConsoleOutput();

                // Patch Console.ReadLine
                PatchConsoleInput();

                // 监听程序集加载
                AppDomain.CurrentDomain.AssemblyLoad += OnAssemblyLoaded;

                Console.WriteLine("[TMLConsolePatch] Harmony patches applied");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[TMLConsolePatch] Failed to apply Harmony patches: {ex.Message}");
                throw;
            }
        }

        private static void OnAssemblyLoaded(object? sender, AssemblyLoadEventArgs args)
        {
            try
            {
                var assemblyName = args.LoadedAssembly.GetName().Name;

                if (assemblyName == "tModLoader")
                {
                    Console.WriteLine("[TMLConsolePatch] tModLoader loaded, applying console patches...");
                    _tModLoaderAssembly = args.LoadedAssembly;
                    PatchConsole();
                    AppDomain.CurrentDomain.AssemblyLoad -= OnAssemblyLoaded;
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[TMLConsolePatch] Error in OnAssemblyLoaded: {ex.Message}");
            }
        }

        private static void PatchConsole()
        {
            try
            {
                if (_tModLoaderAssembly == null)
                    return;

                // Patch Main.Draw 来绘制控制台UI
                var mainType = _tModLoaderAssembly.GetType("Terraria.Main");
                if (mainType != null)
                {
                    var drawMethod = mainType.GetMethod("Draw", BindingFlags.Instance | BindingFlags.NonPublic | BindingFlags.Public);
                    if (drawMethod != null)
                    {
                        var postfix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Main_Draw_Postfix));
                        _harmony!.Patch(drawMethod, postfix: postfix);
                        Console.WriteLine("[TMLConsolePatch] ✓ Patched Main.Draw");
                    }

                    // Patch Main.DoUpdate 来处理输入
                    var updateMethod = mainType.GetMethod("DoUpdate", BindingFlags.Instance | BindingFlags.NonPublic);
                    if (updateMethod != null)
                    {
                        var postfix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Main_DoUpdate_Postfix));
                        _harmony!.Patch(updateMethod, postfix: postfix);
                        Console.WriteLine("[TMLConsolePatch] ✓ Patched Main.DoUpdate");
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[TMLConsolePatch] Error patching console: {ex.Message}");
            }
        }

        private static void PatchConsoleOutput()
        {
            try
            {
                var consoleType = typeof(Console);

                // Patch Console.WriteLine()
                var writeLineMethod = consoleType.GetMethod("WriteLine", BindingFlags.Public | BindingFlags.Static, null, Type.EmptyTypes, null);
                if (writeLineMethod != null)
                {
                    var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_WriteLine_Prefix));
                    _harmony!.Patch(writeLineMethod, prefix: prefix);
                }

                // Patch Console.WriteLine(string)
                var writeLineStringMethod = consoleType.GetMethod("WriteLine", BindingFlags.Public | BindingFlags.Static, null, new[] { typeof(string) }, null);
                if (writeLineStringMethod != null)
                {
                    var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_WriteLineString_Prefix));
                    _harmony!.Patch(writeLineStringMethod, prefix: prefix);
                }

                // Patch Console.Write(string)
                var writeStringMethod = consoleType.GetMethod("Write", BindingFlags.Public | BindingFlags.Static, null, new[] { typeof(string) }, null);
                if (writeStringMethod != null)
                {
                    var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_WriteString_Prefix));
                    _harmony!.Patch(writeStringMethod, prefix: prefix);
                }

                Console.WriteLine("[TMLConsolePatch] ✓ Patched Console output methods");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[TMLConsolePatch] Error patching console output: {ex.Message}");
            }
        }

        private static void PatchConsoleInput()
        {
            try
            {
                var consoleType = typeof(Console);

                // Patch Console.ReadLine()
                var readLineMethod = consoleType.GetMethod("ReadLine", BindingFlags.Public | BindingFlags.Static, null, Type.EmptyTypes, null);
                if (readLineMethod != null)
                {
                    var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_ReadLine_Prefix));
                    _harmony!.Patch(readLineMethod, prefix: prefix);
                    Console.WriteLine("[TMLConsolePatch] ✓ Patched Console.ReadLine");
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[TMLConsolePatch] Error patching console input: {ex.Message}");
            }
        }

        // Console.WriteLine() 补丁
        private static bool Console_WriteLine_Prefix()
        {
            ConsoleManager.AddOutput("");
            return true; // 继续执行原方法
        }

        // Console.WriteLine(string) 补丁
        private static bool Console_WriteLineString_Prefix(string? value)
        {
            ConsoleManager.AddOutput(value ?? "");
            return true;
        }

        // Console.Write(string) 补丁
        private static bool Console_WriteString_Prefix(string? value)
        {
            ConsoleManager.AddOutput(value ?? "");
            return true;
        }

        // Console.ReadLine() 补丁
        private static bool Console_ReadLine_Prefix(ref string? __result)
        {
            // 如果控制台UI启用,从输入队列读取
            if (ConsoleManager.IsConsoleUIEnabled)
            {
                __result = ConsoleManager.WaitForInput();
                return false; // 跳过原方法
            }
            return true; // 执行原方法
        }

        // Main.Draw 后置补丁 - 绘制控制台UI
        private static void Main_Draw_Postfix(object __instance, GameTime gameTime)
        {
            try
            {
                if (!ConsoleManager.IsConsoleUIEnabled)
                    return;

                ConsoleUI.Draw(__instance, gameTime);
            }
            catch (Exception ex)
            {
                // 静默处理绘制错误,避免崩溃
            }
        }

        // Main.DoUpdate 后置补丁 - 处理输入
        private static void Main_DoUpdate_Postfix(object __instance, ref GameTime gameTime)
        {
            try
            {
                if (!ConsoleManager.IsConsoleUIEnabled)
                    return;

                ConsoleUI.Update(__instance, gameTime);
            }
            catch (Exception ex)
            {
                // 静默处理更新错误
            }
        }
    }
}
