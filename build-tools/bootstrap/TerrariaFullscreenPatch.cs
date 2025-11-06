using HarmonyLib;
using System;
using System.Reflection;

/// <summary>
/// Terraria全屏补丁：在Game.Run()前强制设置全屏
/// </summary>
public class TerrariaFullscreenPatch
{
    public static void ApplyFullscreenPatch(string assemblyPath)
    {
        try
        {
            Console.WriteLine("[TerrariaFullscreenPatch] Starting fullscreen patch...");

            Assembly terrariaAssembly = Assembly.LoadFrom(assemblyPath);

            // 获取Terraria.Main类型（验证程序集）
            Type mainType = terrariaAssembly.GetType("Terraria.Main");
            if (mainType == null)
            {
                Console.WriteLine("[TerrariaFullscreenPatch] ⚠️ Terraria.Main class not found");
                return;
            }

            Console.WriteLine("[TerrariaFullscreenPatch] Found Terraria.Main class");

            // 创建Harmony实例
            Harmony harmony = new Harmony("com.ralaunch.terraria.fullscreen");

            // 获取Microsoft.Xna.Framework.Game的Run方法
            Type gameType = mainType.BaseType; // Terraria.Main继承自Game
            while (gameType != null && gameType.Name != "Game")
            {
                gameType = gameType.BaseType;
            }

            if (gameType == null)
            {
                Console.WriteLine("[TerrariaFullscreenPatch] ⚠️ Could not find Game base class");
                return;
            }

            Console.WriteLine($"[TerrariaFullscreenPatch] Found Game base class: {gameType.FullName}");

            // 获取Run方法
            MethodInfo runMethod = gameType.GetMethod("Run", BindingFlags.Public | BindingFlags.Instance);
            if (runMethod == null)
            {
                Console.WriteLine("[TerrariaFullscreenPatch] ⚠️ Run method not found");
                return;
            }

            Console.WriteLine("[TerrariaFullscreenPatch] Found Run method");

            // 创建Prefix补丁
            HarmonyMethod prefix = new HarmonyMethod(typeof(TerrariaFullscreenPatch), nameof(IsFullScreenPatch));

            // 应用补丁
            harmony.Patch(runMethod, prefix);

            Console.WriteLine("[TerrariaFullscreenPatch] ✅ Run Prefix applied successfully!");

            // Patch GraphicsDeviceManager.ToggleFullScreen 防止切换全屏
            try
            {
                Type graphicsDeviceManagerType = gameType.Assembly.GetType("Microsoft.Xna.Framework.GraphicsDeviceManager");
                if (graphicsDeviceManagerType != null)
                {
                    MethodInfo toggleMethod = graphicsDeviceManagerType.GetMethod("ToggleFullScreen", BindingFlags.Public | BindingFlags.Instance);
                    if (toggleMethod != null)
                    {
                        HarmonyMethod togglePrefix = new HarmonyMethod(typeof(TerrariaFullscreenPatch), nameof(ToggleFullScreen_Prefix));
                        harmony.Patch(toggleMethod, togglePrefix);
                        Console.WriteLine("[TerrariaFullscreenPatch] ✅ Patched ToggleFullScreen!");
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[TerrariaFullscreenPatch] ⚠️ Failed to patch ToggleFullScreen: {ex.Message}");
            }

            // 额外patch GraphicsDeviceManager.ApplyChanges 来防止设置被重置
            try
            {
                Type graphicsDeviceManagerType = gameType.Assembly.GetType("Microsoft.Xna.Framework.GraphicsDeviceManager");
                if (graphicsDeviceManagerType != null)
                {
                    MethodInfo applyChangesMethod = graphicsDeviceManagerType.GetMethod("ApplyChanges", BindingFlags.Public | BindingFlags.Instance);
                    if (applyChangesMethod != null)
                    {
                        HarmonyMethod applyChangesPrefix = new HarmonyMethod(typeof(TerrariaFullscreenPatch), nameof(ApplyChanges_Prefix));
                        HarmonyMethod applyChangesPostfix = new HarmonyMethod(typeof(TerrariaFullscreenPatch), nameof(ApplyChanges_Postfix));
                        harmony.Patch(applyChangesMethod, applyChangesPrefix, applyChangesPostfix);
                        Console.WriteLine("[TerrariaFullscreenPatch] ✅ ApplyChanges Prefix+Postfix applied!");
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[TerrariaFullscreenPatch] ⚠️ Failed to patch ApplyChanges: {ex.Message}");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TerrariaFullscreenPatch] ❌ Patch failed: {ex.Message}");
            Console.WriteLine($"[TerrariaFullscreenPatch] Stack trace: {ex.StackTrace}");
        }
    }

    /// <summary>
    /// Game.Run()的Prefix：在运行前强制设置全屏
    /// </summary>
    public static void IsFullScreenPatch(object __instance)
    {
        try
        {
            Console.WriteLine("[TerrariaFullscreenPatch] 🎮 Game.Run() prefix executing...");

            // 获取Terraria.Main的静态字段
            Type terrariaMainType = __instance.GetType();
            Console.WriteLine($"[TerrariaFullscreenPatch] Instance type: {terrariaMainType.FullName}");

            // 设置screenMaximized
            try
            {
                FieldInfo screenMaximizedField = terrariaMainType.GetField("screenMaximized", BindingFlags.Public | BindingFlags.Static);
                if (screenMaximizedField != null)
                {
                    screenMaximizedField.SetValue(null, true);
                    Console.WriteLine("[TerrariaFullscreenPatch] ✅ Set Terraria.Main.screenMaximized = true");
                }
                else
                {
                    Console.WriteLine("[TerrariaFullscreenPatch] ⚠️ screenMaximized field not found");
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[TerrariaFullscreenPatch] ⚠️ Failed to set screenMaximized: {ex.Message}");
            }

            // 设置graphics.IsFullScreen
            try
            {
                FieldInfo graphicsField = terrariaMainType.GetField("graphics", BindingFlags.Public | BindingFlags.Static);
                if (graphicsField != null)
                {
                    object graphics = graphicsField.GetValue(null);
                    if (graphics != null)
                    {
                        PropertyInfo isFullScreenProp = graphics.GetType().GetProperty("IsFullScreen", BindingFlags.Public | BindingFlags.Instance);
                        if (isFullScreenProp != null && isFullScreenProp.CanWrite)
                        {
                            isFullScreenProp.SetValue(graphics, true);
                            Console.WriteLine("[TerrariaFullscreenPatch] ✅ Set Terraria.Main.graphics.IsFullScreen = true");
                        }
                        else
                        {
                            Console.WriteLine("[TerrariaFullscreenPatch] ⚠️ IsFullScreen property not found or not writable");
                        }
                    }
                    else
                    {
                        Console.WriteLine("[TerrariaFullscreenPatch] ⚠️ graphics is null");
                    }
                }
                else
                {
                    Console.WriteLine("[TerrariaFullscreenPatch] ⚠️ graphics field not found");
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[TerrariaFullscreenPatch] ⚠️ Failed to set IsFullScreen: {ex.Message}");
            }

            Console.WriteLine("[TerrariaFullscreenPatch] ✅ Fullscreen patch executed");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TerrariaFullscreenPatch] ❌ IsFullScreenPatch error: {ex.Message}");
            Console.WriteLine($"[TerrariaFullscreenPatch] Stack trace: {ex.StackTrace}");
        }
    }

    /// <summary>
    /// GraphicsDeviceManager.ToggleFullScreen的Prefix：阻止退出全屏
    /// </summary>
    public static bool ToggleFullScreen_Prefix(object __instance)
    {
        Console.WriteLine("[TerrariaFullscreenPatch] 🚫 Blocked ToggleFullScreen attempt - staying fullscreen");
        return false; // 阻止原方法执行
    }

    /// <summary>
    /// GraphicsDeviceManager.ApplyChanges()的Prefix：强制保持全屏和真实分辨率
    /// </summary>
    public static void ApplyChanges_Prefix(object __instance)
    {
        try
        {
            Console.WriteLine("[TerrariaFullscreenPatch] 🔄 ApplyChanges prefix executing...");

            Type graphicsDeviceManagerType = __instance.GetType();

            // 强制设置全屏
            PropertyInfo isFullScreenProp = graphicsDeviceManagerType.GetProperty("IsFullScreen", BindingFlags.Public | BindingFlags.Instance);
            if (isFullScreenProp != null && isFullScreenProp.CanWrite)
            {
                bool currentValue = (bool)isFullScreenProp.GetValue(__instance);
                if (!currentValue)
                {
                    isFullScreenProp.SetValue(__instance, true);
                    Console.WriteLine("[TerrariaFullscreenPatch] ✅ Forced IsFullScreen = true (was false)");
                }
            }

            // 强制设置真实分辨率（2400x1080）
            PropertyInfo widthProp = graphicsDeviceManagerType.GetProperty("PreferredBackBufferWidth", BindingFlags.Public | BindingFlags.Instance);
            PropertyInfo heightProp = graphicsDeviceManagerType.GetProperty("PreferredBackBufferHeight", BindingFlags.Public | BindingFlags.Instance);

            if (widthProp != null && widthProp.CanWrite && heightProp != null && heightProp.CanWrite)
            {
                int currentWidth = (int)widthProp.GetValue(__instance);
                int currentHeight = (int)heightProp.GetValue(__instance);

                if (currentWidth != 2400 || currentHeight != 1080)
                {
                    widthProp.SetValue(__instance, 2400);
                    heightProp.SetValue(__instance, 1080);
                    Console.WriteLine($"[TerrariaFullscreenPatch] ✅ Forced resolution 2400x1080 (was {currentWidth}x{currentHeight})");
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TerrariaFullscreenPatch] ⚠️ ApplyChanges_Prefix error: {ex.Message}");
        }
    }

    /// <summary>
    /// GraphicsDeviceManager.ApplyChanges()的Postfix：在应用后强制修正Terraria.Main的字段
    /// </summary>
    public static void ApplyChanges_Postfix()
    {
        try
        {
            // 通过反射获取Terraria.Main类型
            Type terrariaMainType = Type.GetType("Terraria.Main, tModLoader");
            if (terrariaMainType != null)
            {
                // 强制修正screenWidth和screenHeight字段
                FieldInfo screenWidthField = terrariaMainType.GetField("screenWidth", BindingFlags.Public | BindingFlags.Static);
                FieldInfo screenHeightField = terrariaMainType.GetField("screenHeight", BindingFlags.Public | BindingFlags.Static);

                if (screenWidthField != null && screenHeightField != null)
                {
                    int currentWidth = (int)screenWidthField.GetValue(null);
                    int currentHeight = (int)screenHeightField.GetValue(null);

                    if (currentWidth != 2400 || currentHeight != 1080)
                    {
                        screenWidthField.SetValue(null, 2400);
                        screenHeightField.SetValue(null, 1080);
                        Console.WriteLine($"[TerrariaFullscreenPatch] 🔧 Postfix: Fixed Terraria.Main fields from {currentWidth}x{currentHeight} to 2400x1080");
                    }
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TerrariaFullscreenPatch] ⚠️ ApplyChanges_Postfix error: {ex.Message}");
        }
    }
}

