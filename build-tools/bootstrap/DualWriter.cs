using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;

namespace AssemblyMain
{
    /// <summary>
    /// 将Console输出重定向到Android Logcat
    /// </summary>
    public class DualWriter : TextWriter
    {
        private const string TAG = "Bootstrap";
        private readonly StringBuilder _lineBuffer = new StringBuilder();

        // Android日志级别
        private enum AndroidLogLevel
        {
            Verbose = 2,
            Debug = 3,
            Info = 4,
            Warn = 5,
            Error = 6
        }

        /// <summary>
        /// Android logcat native函数
        /// </summary>
        [DllImport("liblog.so", EntryPoint = "__android_log_write")]
        private static extern int AndroidLogWrite(int priority, string tag, string msg);

        public override Encoding Encoding => Encoding.UTF8;

        public override void Write(char value)
        {
            if (value == '\n')
            {
                Flush();
            }
            else if (value != '\r')
            {
                _lineBuffer.Append(value);
            }
        }

        public override void WriteLine(string value)
        {
            if (!string.IsNullOrEmpty(value))
            {
                AndroidLogWrite((int)AndroidLogLevel.Info, TAG, value);
            }
        }

        public override void WriteLine()
        {
            Flush();
        }

        public override void Flush()
        {
            if (_lineBuffer.Length > 0)
            {
                AndroidLogWrite((int)AndroidLogLevel.Info, TAG, _lineBuffer.ToString());
                _lineBuffer.Clear();
            }
        }

        /// <summary>
        /// 初始化Console重定向到logcat
        /// </summary>
        public static void Initialize()
        {
            try
            {
                var writer = new DualWriter();
                Console.SetOut(writer);
                Console.SetError(writer);
                Console.WriteLine("[DualWriter] Console output redirected to logcat");
            }
            catch (Exception ex)
            {
                // 如果重定向失败，至少要记录错误（但可能看不到）
                System.Diagnostics.Debug.WriteLine($"[DualWriter] Failed to redirect console: {ex.Message}");
            }
        }
    }
}

