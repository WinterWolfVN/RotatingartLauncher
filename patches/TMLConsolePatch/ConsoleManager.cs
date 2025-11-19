using System;
using System.Collections.Generic;
using System.Text;
using System.Threading;

namespace TMLConsolePatch
{
    /// <summary>
    /// 控制台管理器 - 管理控制台输出缓冲和输入队列
    /// </summary>
    public static class ConsoleManager
    {
        // 控制台输出缓冲区 (最多保留 1000 行)
        private static readonly List<string> _outputBuffer = new();
        private static readonly object _outputLock = new();
        private const int MaxOutputLines = 1000;

        // 控制台输入队列
        private static readonly Queue<string> _inputQueue = new();
        private static readonly object _inputLock = new();
        private static readonly ManualResetEvent _inputAvailable = new(false);

        // 是否启用控制台UI
        public static bool IsConsoleUIEnabled { get; set; } = true;

        /// <summary>
        /// 添加输出到缓冲区
        /// </summary>
        public static void AddOutput(string text)
        {
            if (string.IsNullOrEmpty(text))
                return;

            lock (_outputLock)
            {
                _outputBuffer.Add(text);

                // 限制缓冲区大小
                while (_outputBuffer.Count > MaxOutputLines)
                {
                    _outputBuffer.RemoveAt(0);
                }
            }
        }

        /// <summary>
        /// 获取所有输出行
        /// </summary>
        public static List<string> GetOutputLines()
        {
            lock (_outputLock)
            {
                return new List<string>(_outputBuffer);
            }
        }

        /// <summary>
        /// 获取最近的输出行
        /// </summary>
        public static List<string> GetRecentOutputLines(int count)
        {
            lock (_outputLock)
            {
                int startIndex = Math.Max(0, _outputBuffer.Count - count);
                int takeCount = Math.Min(count, _outputBuffer.Count);
                return _outputBuffer.GetRange(startIndex, takeCount);
            }
        }

        /// <summary>
        /// 清空输出缓冲区
        /// </summary>
        public static void ClearOutput()
        {
            lock (_outputLock)
            {
                _outputBuffer.Clear();
            }
        }

        /// <summary>
        /// 提交输入到队列
        /// </summary>
        public static void SubmitInput(string input)
        {
            lock (_inputLock)
            {
                _inputQueue.Enqueue(input);
                _inputAvailable.Set();
            }
        }

        /// <summary>
        /// 等待并读取输入 (阻塞调用)
        /// </summary>
        public static string? WaitForInput(int timeoutMs = -1)
        {
            if (timeoutMs >= 0)
            {
                if (!_inputAvailable.WaitOne(timeoutMs))
                {
                    return null; // 超时
                }
            }
            else
            {
                _inputAvailable.WaitOne();
            }

            lock (_inputLock)
            {
                if (_inputQueue.Count > 0)
                {
                    string input = _inputQueue.Dequeue();

                    // 如果队列为空,重置信号
                    if (_inputQueue.Count == 0)
                    {
                        _inputAvailable.Reset();
                    }

                    return input;
                }
            }

            return null;
        }

        /// <summary>
        /// 尝试读取输入 (非阻塞)
        /// </summary>
        public static bool TryReadInput(out string? input)
        {
            lock (_inputLock)
            {
                if (_inputQueue.Count > 0)
                {
                    input = _inputQueue.Dequeue();

                    // 如果队列为空,重置信号
                    if (_inputQueue.Count == 0)
                    {
                        _inputAvailable.Reset();
                    }

                    return true;
                }
            }

            input = null;
            return false;
        }

        /// <summary>
        /// 检查是否有待处理的输入
        /// </summary>
        public static bool HasPendingInput()
        {
            lock (_inputLock)
            {
                return _inputQueue.Count > 0;
            }
        }
    }
}
