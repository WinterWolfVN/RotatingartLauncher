using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;

namespace TMLConsolePatch
{
    /// <summary>
    /// 服务器命令处理器
    /// </summary>
    public static class ServerCommands
    {
        private static bool _serverStarted = false;

        public static void ProcessServerCommand(string command)
        {
            var parts = command.Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries);
            if (parts.Length < 2)
            {
                ConsoleManager.AddOutput("用法: server <start|stop> [参数]");
                return;
            }

            string subCommand = parts[1].ToLower();

            switch (subCommand)
            {
                case "start":
                    StartServer(parts.Skip(2).ToArray());
                    break;

                case "stop":
                    StopServer();
                    break;

                default:
                    ConsoleManager.AddOutput($"未知的服务器命令: {subCommand}");
                    ConsoleManager.AddOutput("可用命令: start, stop");
                    break;
            }
        }

        private static void StartServer(string[] args)
        {
            ConsoleManager.AddOutput("========================================");
            ConsoleManager.AddOutput("Android 平台多人游戏说明:");
            ConsoleManager.AddOutput("========================================");
            ConsoleManager.AddOutput("");
            ConsoleManager.AddOutput("重要提示：");
            ConsoleManager.AddOutput("由于 Android 系统限制，无法在同一设备上");
            ConsoleManager.AddOutput("同时运行服务器和客户端。");
            ConsoleManager.AddOutput("");
            ConsoleManager.AddOutput("方案 1 - 本设备作为服务器:");
            ConsoleManager.AddOutput("1. 通过 RA Launcher 启动 tModLoader");
            ConsoleManager.AddOutput("2. 在补丁管理中启用 'tml_server_mode' 补丁");
            ConsoleManager.AddOutput("3. 重启游戏，将以专用服务器模式运行");
            ConsoleManager.AddOutput("4. 使用其他设备连接到本设备 IP");
            ConsoleManager.AddOutput("");
            ConsoleManager.AddOutput("方案 2 - 连接到其他服务器:");
            ConsoleManager.AddOutput("1. 确保禁用 'tml_server_mode' 补丁");
            ConsoleManager.AddOutput("2. 启动游戏，选择 '多人游戏' -> '加入'");
            ConsoleManager.AddOutput("3. 输入服务器地址和端口");
            ConsoleManager.AddOutput("");
            ConsoleManager.AddOutput("获取本设备 IP 地址:");
            ConsoleManager.AddOutput("- 设置 -> WLAN -> 当前网络详情");
            ConsoleManager.AddOutput("- 或使用命令: ip addr show wlan0");
            ConsoleManager.AddOutput("========================================");
        }

        private static void StopServer()
        {
            ConsoleManager.AddOutput("========================================");
            ConsoleManager.AddOutput("Android 平台服务器停止说明:");
            ConsoleManager.AddOutput("========================================");
            ConsoleManager.AddOutput("");
            ConsoleManager.AddOutput("要停止服务器，请:");
            ConsoleManager.AddOutput("1. 返回 RA Launcher 主界面");
            ConsoleManager.AddOutput("2. 在通知栏中找到服务器状态");
            ConsoleManager.AddOutput("3. 点击 '停止服务器' 按钮");
            ConsoleManager.AddOutput("");
            ConsoleManager.AddOutput("或者在服务器管理界面中点击停止按钮。");
            ConsoleManager.AddOutput("========================================");
        }
    }
}
