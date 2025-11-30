using System;
using System.Reflection;
using System.Runtime.InteropServices;
using HarmonyLib;

namespace ConsolePatch;

/// <summary>
/// Android 平台兼容补丁
/// 修复 Android 平台不支持的 Console 和 PosixSignalRegistration 等功能
/// </summary>
public static class ConsolePatcher
{
    private static Harmony? _harmony;
    private static bool _initialized = false;

    /// <summary>
    /// 补丁初始化方法
    /// 会在游戏程序集加载前被自动调用
    /// </summary>
    public static int Initialize(IntPtr arg, int sizeBytes)
    {
        if (_initialized)
        {
            Console.WriteLine("[ConsolePatch] Already initialized, skipping...");
            return 0;
        }

        try
        {
            Console.WriteLine("========================================");
            Console.WriteLine("[ConsolePatch] Android Console Compatibility Patch");
            Console.WriteLine("========================================");
            Console.WriteLine("[ConsolePatch] Patching Console properties for Android...");

            _harmony = new Harmony("com.ralaunch.consolepatch");

            // Patch Console.Title setter
            PatchConsoleTitleSetter();

            // Patch Console.ForegroundColor setter
            PatchConsoleForegroundColorSetter();

            // Patch Console.BackgroundColor setter (also not supported)
            PatchConsoleBackgroundColorSetter();

            // Patch Console.ResetColor (also not supported)
            PatchConsoleResetColor();

            // Patch Console.Clear (also not supported)
            PatchConsoleClear();

            // Patch PosixSignalRegistration.Create (not supported on Android)
            PatchPosixSignalRegistration();

            _initialized = true;
            Console.WriteLine("[ConsolePatch] All console patches applied!");
            Console.WriteLine("========================================");

            return 0; // 成功
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsolePatch] ERROR: {ex.Message}");
            Console.WriteLine($"[ConsolePatch] Stack: {ex.StackTrace}");
            return -1; // 失败
        }
    }

    /// <summary>
    /// Patch Console.Title setter
    /// </summary>
    private static void PatchConsoleTitleSetter()
    {
        try
        {
            var titleProperty = typeof(Console).GetProperty("Title", BindingFlags.Public | BindingFlags.Static);
            var setMethod = titleProperty?.GetSetMethod();

            if (setMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_SetTitle_Prefix));
                _harmony!.Patch(setMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.Title setter patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.Title setter not found (may be already unsupported)");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsolePatch] Failed to patch Console.Title: {ex.Message}");
        }
    }

    /// <summary>
    /// Patch Console.ForegroundColor setter
    /// </summary>
    private static void PatchConsoleForegroundColorSetter()
    {
        try
        {
            var colorProperty = typeof(Console).GetProperty("ForegroundColor", BindingFlags.Public | BindingFlags.Static);
            var setMethod = colorProperty?.GetSetMethod();

            if (setMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_SetColor_Prefix));
                _harmony!.Patch(setMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.ForegroundColor setter patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.ForegroundColor setter not found");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsolePatch] Failed to patch Console.ForegroundColor: {ex.Message}");
        }
    }

    /// <summary>
    /// Patch Console.BackgroundColor setter
    /// </summary>
    private static void PatchConsoleBackgroundColorSetter()
    {
        try
        {
            var colorProperty = typeof(Console).GetProperty("BackgroundColor", BindingFlags.Public | BindingFlags.Static);
            var setMethod = colorProperty?.GetSetMethod();

            if (setMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_SetColor_Prefix));
                _harmony!.Patch(setMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.BackgroundColor setter patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.BackgroundColor setter not found");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsolePatch] Failed to patch Console.BackgroundColor: {ex.Message}");
        }
    }

    /// <summary>
    /// Patch Console.ResetColor
    /// </summary>
    private static void PatchConsoleResetColor()
    {
        try
        {
            var resetMethod = typeof(Console).GetMethod("ResetColor", BindingFlags.Public | BindingFlags.Static);

            if (resetMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_ResetColor_Prefix));
                _harmony!.Patch(resetMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.ResetColor patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.ResetColor not found");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsolePatch] Failed to patch Console.ResetColor: {ex.Message}");
        }
    }

    /// <summary>
    /// Prefix for Console.Title setter - skip the original method
    /// </summary>
    public static bool Console_SetTitle_Prefix(string value)
    {
        // Do nothing - Android doesn't support console title
        // Just silently ignore the call
        return false; // Skip original method
    }

    /// <summary>
    /// Prefix for Console.ForegroundColor/BackgroundColor setters - skip the original method
    /// </summary>
    public static bool Console_SetColor_Prefix()
    {
        // Do nothing - Android doesn't support console colors
        // Just silently ignore the call
        return false; // Skip original method
    }

    /// <summary>
    /// Prefix for Console.ResetColor - skip the original method
    /// </summary>
    public static bool Console_ResetColor_Prefix()
    {
        // Do nothing - Android doesn't support console colors
        // Just silently ignore the call
        return false; // Skip original method
    }

    /// <summary>
    /// Patch Console.Clear
    /// </summary>
    private static void PatchConsoleClear()
    {
        try
        {
            var clearMethod = typeof(Console).GetMethod("Clear", BindingFlags.Public | BindingFlags.Static);

            if (clearMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_Clear_Prefix));
                _harmony!.Patch(clearMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.Clear patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.Clear not found");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsolePatch] Failed to patch Console.Clear: {ex.Message}");
        }
    }

    /// <summary>
    /// Prefix for Console.Clear - skip the original method
    /// </summary>
    public static bool Console_Clear_Prefix()
    {
        // Do nothing - Android doesn't support console clear
        // Just silently ignore the call
        return false; // Skip original method
    }

    /// <summary>
    /// Patch PosixSignalRegistration.Create - not supported on Android
    /// </summary>
    private static void PatchPosixSignalRegistration()
    {
        try
        {
            // PosixSignalRegistration is in System.Runtime.InteropServices
            var posixSignalRegType = typeof(PosixSignalRegistration);

            if (posixSignalRegType == null)
            {
                Console.WriteLine("[ConsolePatch] PosixSignalRegistration type not found");
                return;
            }

            // Find the Create method
            var createMethod = posixSignalRegType.GetMethod("Create", BindingFlags.Public | BindingFlags.Static);

            if (createMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(PosixSignalRegistration_Create_Prefix));
                _harmony!.Patch(createMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] PosixSignalRegistration.Create patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] PosixSignalRegistration.Create method not found");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsolePatch] Failed to patch PosixSignalRegistration.Create: {ex.Message}");
        }
    }

    /// <summary>
    /// Prefix for PosixSignalRegistration.Create - return null instead of throwing
    /// </summary>
    public static bool PosixSignalRegistration_Create_Prefix(ref PosixSignalRegistration? __result)
    {
        // Return null - Android doesn't support POSIX signal registration
        // The caller should handle null gracefully
        __result = null;
        return false; // Skip original method
    }
}

