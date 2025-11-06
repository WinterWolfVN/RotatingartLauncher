using System;
using System.Diagnostics;
using System.Runtime;
using System.Text;
using System.Threading;

namespace AssemblyMain
{
    /// <summary>
    /// C# 性能监控器 - 监控托管内存、GC、帧率等
    /// </summary>
    public static class PerformanceMonitor
    {
        private static Thread _monitorThread;
        private static bool _isRunning;
        private static readonly object _lock = new object();
        
        // 性能指标
        private static long _lastGen0Count;
        private static long _lastGen1Count;
        private static long _lastGen2Count;
        private static DateTime _lastGcCheckTime;
        private static long _gcCollectionCount;
        private static long _totalGcPauseTime;
        
        // 内存泄漏检测
        private static long _lastManagedMemory;
        private static long _managedMemoryGrowthCounter;
        private static DateTime _lastMemoryCheckTime;
        
        // FPS计数
        private static int _frameCount;
        private static DateTime _lastFpsCheckTime;
        private static double _currentFps;
        
        // 配置
        private static int _monitorIntervalMs = 1000; // 监控间隔（毫秒）
        private static int _logIntervalSeconds = 5;   // 日志输出间隔（秒）
        private static long _memoryLeakThreshold = 50 * 1024 * 1024; // 50MB增长阈值
        
        /// <summary>
        /// 启动性能监控
        /// </summary>
        public static void Start()
        {
            lock (_lock)
            {
                if (_isRunning)
                {
                    Console.WriteLine("[PerformanceMonitor] Already running");
                    return;
                }
                
                _isRunning = true;
                _lastGcCheckTime = DateTime.UtcNow;
                _lastMemoryCheckTime = DateTime.UtcNow;
                _lastFpsCheckTime = DateTime.UtcNow;
                
                _lastGen0Count = GC.CollectionCount(0);
                _lastGen1Count = GC.CollectionCount(1);
                _lastGen2Count = GC.CollectionCount(2);
                _lastManagedMemory = GC.GetTotalMemory(false);
                
                _monitorThread = new Thread(MonitorLoop)
                {
                    Name = "PerformanceMonitor",
                    IsBackground = true
                };
                _monitorThread.Start();
                
                Console.WriteLine("[PerformanceMonitor] ✅ Started");
            }
        }
        
        /// <summary>
        /// 停止性能监控
        /// </summary>
        public static void Stop()
        {
            lock (_lock)
            {
                if (!_isRunning)
                    return;
                
                _isRunning = false;
                _monitorThread?.Join(2000);
                
                Console.WriteLine("[PerformanceMonitor] Stopped");
            }
        }
        
        /// <summary>
        /// 监控循环
        /// </summary>
        private static void MonitorLoop()
        {
            DateTime lastLogTime = DateTime.UtcNow;
            
            while (_isRunning)
            {
                try
                {
                    // 收集性能指标
                    CollectMetrics();
                    
                    // 检测内存泄漏
                    CheckMemoryLeak();
                    
                    // 定期输出日志
                    if ((DateTime.UtcNow - lastLogTime).TotalSeconds >= _logIntervalSeconds)
                    {
                        LogPerformanceMetrics();
                        lastLogTime = DateTime.UtcNow;
                    }
                    
                    Thread.Sleep(_monitorIntervalMs);
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[PerformanceMonitor] Error: {ex.Message}");
                }
            }
        }
        
        /// <summary>
        /// 收集性能指标
        /// </summary>
        private static void CollectMetrics()
        {
            DateTime now = DateTime.UtcNow;
            
            // GC统计
            long gen0Count = GC.CollectionCount(0);
            long gen1Count = GC.CollectionCount(1);
            long gen2Count = GC.CollectionCount(2);
            
            if (gen0Count != _lastGen0Count || gen1Count != _lastGen1Count || gen2Count != _lastGen2Count)
            {
                _gcCollectionCount++;
                _lastGen0Count = gen0Count;
                _lastGen1Count = gen1Count;
                _lastGen2Count = gen2Count;
            }
            
            // 计算FPS
            double elapsedSeconds = (now - _lastFpsCheckTime).TotalSeconds;
            if (elapsedSeconds >= 1.0)
            {
                _currentFps = _frameCount / elapsedSeconds;
                _frameCount = 0;
                _lastFpsCheckTime = now;
            }
        }
        
        /// <summary>
        /// 检测内存泄漏
        /// </summary>
        private static void CheckMemoryLeak()
        {
            DateTime now = DateTime.UtcNow;
            double elapsedSeconds = (now - _lastMemoryCheckTime).TotalSeconds;
            
            if (elapsedSeconds < 10) // 每10秒检查一次
                return;
            
            long currentMemory = GC.GetTotalMemory(false);
            long memoryGrowth = currentMemory - _lastManagedMemory;
            
            if (memoryGrowth > _memoryLeakThreshold)
            {
                _managedMemoryGrowthCounter++;
                Console.WriteLine($"[PerformanceMonitor] ⚠️ 内存持续增长: +{memoryGrowth / (1024.0 * 1024.0):F2}MB (次数: {_managedMemoryGrowthCounter})");
                
                if (_managedMemoryGrowthCounter >= 5)
                {
                    Console.WriteLine("[PerformanceMonitor] 🔴 疑似内存泄漏！建议检查对象引用");
                    LogMemorySnapshot();
                }
            }
            else if (memoryGrowth < 0)
            {
                // 内存被回收，重置计数器
                _managedMemoryGrowthCounter = 0;
            }
            
            _lastManagedMemory = currentMemory;
            _lastMemoryCheckTime = now;
        }
        
        /// <summary>
        /// 输出性能日志
        /// </summary>
        private static void LogPerformanceMetrics()
        {
            // 托管内存
            long managedMemory = GC.GetTotalMemory(false);
            long managedMemoryMB = managedMemory / (1024 * 1024);
            
            // GC统计
            int gen0 = GC.CollectionCount(0);
            int gen1 = GC.CollectionCount(1);
            int gen2 = GC.CollectionCount(2);
            
            // GC延迟模式
            GCLatencyMode latencyMode = GCSettings.LatencyMode;
            
            var sb = new StringBuilder();
            sb.AppendLine("═══════════════════════════════════════════════");
            sb.AppendLine($"[C# Performance] FPS: {_currentFps:F1}");
            sb.AppendLine($"[C# Memory] Managed: {managedMemoryMB}MB");
            sb.AppendLine($"[C# GC] Gen0:{gen0} Gen1:{gen1} Gen2:{gen2}");
            sb.AppendLine($"[C# GC Mode] {latencyMode}");
            
            // 如果有内存增长警告
            if (_managedMemoryGrowthCounter > 0)
            {
                sb.AppendLine($"[C# Warning] 内存增长检测: {_managedMemoryGrowthCounter}次");
            }
            
            sb.AppendLine("═══════════════════════════════════════════════");
            
            Console.WriteLine(sb.ToString());
        }
        
        /// <summary>
        /// 输出内存快照（用于调试内存泄漏）
        /// </summary>
        private static void LogMemorySnapshot()
        {
            try
            {
                Console.WriteLine("[PerformanceMonitor] 📸 内存快照:");
                
                // 强制完整GC
                long beforeGc = GC.GetTotalMemory(false);
                GC.Collect(2, GCCollectionMode.Forced, true, true);
                GC.WaitForPendingFinalizers();
                GC.Collect(2, GCCollectionMode.Forced, true, true);
                long afterGc = GC.GetTotalMemory(true);
                
                Console.WriteLine($"  GC前: {beforeGc / (1024.0 * 1024.0):F2}MB");
                Console.WriteLine($"  GC后: {afterGc / (1024.0 * 1024.0):F2}MB");
                Console.WriteLine($"  回收: {(beforeGc - afterGc) / (1024.0 * 1024.0):F2}MB");
                
                // GC堆信息
                var gcInfo = GC.GetGCMemoryInfo();
                Console.WriteLine($"  堆大小: {gcInfo.HeapSizeBytes / (1024.0 * 1024.0):F2}MB");
                Console.WriteLine($"  碎片: {gcInfo.FragmentedBytes / (1024.0 * 1024.0):F2}MB");
                Console.WriteLine($"  提交: {gcInfo.TotalCommittedBytes / (1024.0 * 1024.0):F2}MB");
                
                // 线程池信息
                ThreadPool.GetAvailableThreads(out int workerThreads, out int completionPortThreads);
                ThreadPool.GetMaxThreads(out int maxWorkerThreads, out int maxCompletionPortThreads);
                Console.WriteLine($"  工作线程: {maxWorkerThreads - workerThreads}/{maxWorkerThreads}");
                Console.WriteLine($"  IO线程: {maxCompletionPortThreads - completionPortThreads}/{maxCompletionPortThreads}");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[PerformanceMonitor] 内存快照失败: {ex.Message}");
            }
        }
        
        /// <summary>
        /// 记录一帧（用于FPS计算）
        /// </summary>
        public static void RecordFrame()
        {
            Interlocked.Increment(ref _frameCount);
        }
        
        /// <summary>
        /// 获取当前FPS
        /// </summary>
        public static double GetCurrentFPS()
        {
            return _currentFps;
        }
        
        /// <summary>
        /// 获取托管内存使用（MB）
        /// </summary>
        public static long GetManagedMemoryMB()
        {
            return GC.GetTotalMemory(false) / (1024 * 1024);
        }
        
        /// <summary>
        /// 手动触发GC分析（调试用）
        /// </summary>
        public static void ForceGCAnalysis()
        {
            Console.WriteLine("[PerformanceMonitor] 🔍 强制GC分析...");
            LogMemorySnapshot();
        }
    }
}

