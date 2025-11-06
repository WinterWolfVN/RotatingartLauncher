using HarmonyLib;
using System;
using System.Reflection;

namespace AssemblyMain
{
    /// <summary>
    /// FNA性能钩子 - 拦截Game.Draw来计算FPS
    /// </summary>
    public static class FnaPerformanceHook
    {
        private static bool _isPatched = false;
        
        /// <summary>
        /// 应用性能监控补丁
        /// </summary>
        public static void ApplyPatch()
        {
            if (_isPatched)
            {
                Console.WriteLine("[FnaPerformanceHook] Already patched");
                return;
            }
            
            try
            {
                var harmony = new Harmony("com.ralaunch.fna_performance_hook");
                
                // 查找Microsoft.Xna.Framework.Game类
                Type gameType = Type.GetType("Microsoft.Xna.Framework.Game, FNA") 
                    ?? Type.GetType("Microsoft.Xna.Framework.Game, MonoGame.Framework");
                
                if (gameType == null)
                {
                    Console.WriteLine("[FnaPerformanceHook] ⚠️ Game class not found, trying to find in loaded assemblies...");
                    
                    // 尝试从已加载的程序集中查找
                    foreach (var assembly in AppDomain.CurrentDomain.GetAssemblies())
                    {
                        gameType = assembly.GetType("Microsoft.Xna.Framework.Game");
                        if (gameType != null)
                        {
                            Console.WriteLine($"[FnaPerformanceHook] Found Game class in: {assembly.FullName}");
                            break;
                        }
                    }
                }
                
                if (gameType == null)
                {
                    Console.WriteLine("[FnaPerformanceHook] ⚠️ Cannot find Game class, FPS monitoring disabled");
                    return;
                }
                
                // Patch Draw方法
                MethodInfo drawMethod = gameType.GetMethod("Draw", BindingFlags.Instance | BindingFlags.NonPublic | BindingFlags.Public);
                if (drawMethod != null)
                {
                    MethodInfo postfix = typeof(FnaPerformanceHook).GetMethod(nameof(DrawPostfix), BindingFlags.Static | BindingFlags.NonPublic);
                    harmony.Patch(drawMethod, postfix: new HarmonyMethod(postfix));
                    Console.WriteLine("[FnaPerformanceHook] ✅ Draw method patched for FPS monitoring");
                }
                else
                {
                    Console.WriteLine("[FnaPerformanceHook] ⚠️ Draw method not found");
                }
                
                // Patch Update方法（用于性能分析）
                MethodInfo updateMethod = gameType.GetMethod("Update", BindingFlags.Instance | BindingFlags.NonPublic | BindingFlags.Public);
                if (updateMethod != null)
                {
                    MethodInfo prefix = typeof(FnaPerformanceHook).GetMethod(nameof(UpdatePrefix), BindingFlags.Static | BindingFlags.NonPublic);
                    harmony.Patch(updateMethod, prefix: new HarmonyMethod(prefix));
                    Console.WriteLine("[FnaPerformanceHook] ✅ Update method patched for performance analysis");
                }
                
                _isPatched = true;
                Console.WriteLine("[FnaPerformanceHook] ✅ Performance hooks installed");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[FnaPerformanceHook] ⚠️ Failed to apply patch: {ex.Message}");
                Console.WriteLine($"[FnaPerformanceHook] Stack trace: {ex.StackTrace}");
            }
        }
        
        /// <summary>
        /// Draw方法的Postfix - 记录每一帧
        /// </summary>
        private static void DrawPostfix()
        {
            PerformanceMonitor.RecordFrame();
        }
        
        /// <summary>
        /// Update方法的Prefix - 检测卡顿
        /// </summary>
        private static void UpdatePrefix()
        {
            // 可以在这里添加更多性能分析逻辑
            // 例如：检测单帧耗时过长等
        }
    }
}

