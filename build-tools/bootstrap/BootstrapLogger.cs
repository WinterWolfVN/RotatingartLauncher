using System;

namespace AssemblyMain
{
    public static class BootstrapLogger
    {
        // 日志级别: 0=ERROR, 1=WARN, 2=INFO, 3=DEBUG
        private static int _logLevel = GetLogLevelFromEnv();

        private static int GetLogLevelFromEnv()
        {
            string? level = Environment.GetEnvironmentVariable("BOOTSTRAP_LOG_LEVEL");
            if (int.TryParse(level, out int result))
                return result;
            return 1; // 默认只显示 ERROR 和 WARN
        }

        public static void Error(string message)
        {
            if (_logLevel >= 0)
                Console.WriteLine($"[Bootstrap] ❌ {message}");
        }

        public static void Warn(string message)
        {
            if (_logLevel >= 1)
                Console.WriteLine($"[Bootstrap] ⚠️ {message}");
        }

        public static void Info(string message)
        {
            if (_logLevel >= 2)
                Console.WriteLine($"[Bootstrap] ℹ️ {message}");
        }

        public static void Debug(string message)
        {
            if (_logLevel >= 3)
                Console.WriteLine($"[Bootstrap] 🔍 {message}");
        }
    }
}

