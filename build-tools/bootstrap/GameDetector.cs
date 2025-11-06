using System;
using System.IO;
using System.Reflection;
using System.Linq;

namespace AssemblyMain
{
    /// <summary>
    /// 游戏检测器 - 检测游戏类型和模组加载器
    /// </summary>
    public static class GameDetector
    {
        public enum GameType
        {
            Unknown,
            Terraria,
            TerrariatModLoader,  // Terraria with tModLoader
            Other
        }
        
        public class GameInfo
        {
            public GameType Type { get; set; }
            public string GameName { get; set; }
            public string AssemblyName { get; set; }
            public bool IstModLoader { get; set; }
            public bool RequiresOSPlatformPatch { get; set; }
            public bool RequiresFullscreenPatch { get; set; }
            public bool RequiresFileCasingsPatch { get; set; }
            
            public GameInfo()
            {
                Type = GameType.Unknown;
                GameName = "Unknown";
                AssemblyName = "";
                IstModLoader = false;
                RequiresOSPlatformPatch = false;
                RequiresFullscreenPatch = false;
                RequiresFileCasingsPatch = false;
            }
        }
        
        /// <summary>
        /// 检测游戏类型和所需补丁
        /// </summary>
        public static GameInfo DetectGame(string targetGamePath, Assembly targetAssembly = null)
        {
            var info = new GameInfo();
            
            try
            {
                string fileName = Path.GetFileNameWithoutExtension(targetGamePath);
                info.AssemblyName = fileName;
                
                // 检测Terraria
                if (fileName.Equals("tModLoader", StringComparison.OrdinalIgnoreCase) ||
                    fileName.Equals("Terraria", StringComparison.OrdinalIgnoreCase))
                {
                    // 检查是否是tModLoader
                    bool istModLoader = DetecttModLoader(targetAssembly, targetGamePath);
                    
                    if (istModLoader)
                    {
                        info.Type = GameType.TerrariatModLoader;
                        info.GameName = "Terraria (tModLoader)";
                        info.IstModLoader = true;
                        info.RequiresOSPlatformPatch = false;
                        info.RequiresFullscreenPatch = true;
                        info.RequiresFileCasingsPatch = true;
                    }
                    else
                    {
                        info.Type = GameType.Terraria;
                        info.GameName = "Terraria (Vanilla)";
                        info.IstModLoader = false;
                        info.RequiresOSPlatformPatch = true;
                        info.RequiresFullscreenPatch = true;
                        info.RequiresFileCasingsPatch = true;
                    }
                }
                else
                {
                    info.Type = GameType.Other;
                    info.GameName = fileName;
                    info.RequiresOSPlatformPatch = false;
                    info.RequiresFullscreenPatch = false;
                    info.RequiresFileCasingsPatch = true;
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[GameDetector] Detection failed: {ex.Message}");
            }
            
            return info;
        }
        
        /// <summary>
        /// 检测是否是tModLoader
        /// </summary>
        private static bool DetecttModLoader(Assembly targetAssembly, string targetGamePath)
        {
            try
            {
                // 检查程序集名称
                string fileName = Path.GetFileNameWithoutExtension(targetGamePath);
                if (fileName.Equals("tModLoader", StringComparison.OrdinalIgnoreCase))
                    return true;
                
                // 检查是否存在tModLoader特有的类型
                if (targetAssembly != null)
                {
                    var modLoaderTypes = targetAssembly.GetTypes()
                        .Where(t => t.Namespace != null && t.Namespace.StartsWith("Terraria.ModLoader"))
                        .ToArray();
                    
                    if (modLoaderTypes.Length > 0)
                        return true;
                    
                    if (targetAssembly.GetType("Terraria.ModLoader.ModLoader") != null)
                        return true;
                }
                
                // 检查是否存在tModLoader.dll文件
                string gameDir = Path.GetDirectoryName(targetGamePath);
                string tModLoaderDll = Path.Combine(gameDir, "tModLoader.dll");
                if (File.Exists(tModLoaderDll))
                    return true;
                
                return false;
            }
            catch
            {
                return false;
            }
        }
        
        /// <summary>
        /// 打印游戏信息摘要
        /// </summary>
        public static void PrintGameInfo(GameInfo info)
        {
            Console.WriteLine($"");
            Console.WriteLine($"╔═══════════════════════════════════════════════╗");
            Console.WriteLine($"║          Game Detection Summary               ║");
            Console.WriteLine($"╠═══════════════════════════════════════════════╣");
            Console.WriteLine($"║ Game: {info.GameName.PadRight(40)} ║");
            Console.WriteLine($"║ Type: {info.Type.ToString().PadRight(40)} ║");
            Console.WriteLine($"║ tModLoader: {(info.IstModLoader ? "Yes" : "No ").PadRight(36)} ║");
            Console.WriteLine($"╠═══════════════════════════════════════════════╣");
            Console.WriteLine($"║ Required Patches:                             ║");
            Console.WriteLine($"║   • OS Platform Patch:   {(info.RequiresOSPlatformPatch ? "[✓]" : "[✗]").PadRight(24)} ║");
            Console.WriteLine($"║   • Fullscreen Patch:    {(info.RequiresFullscreenPatch ? "[✓]" : "[✗]").PadRight(24)} ║");
            Console.WriteLine($"║   • File Casings Patch:  {(info.RequiresFileCasingsPatch ? "[✓]" : "[✗]").PadRight(24)} ║");
            Console.WriteLine($"╚═══════════════════════════════════════════════╝");
            Console.WriteLine($"");
        }
    }
}

