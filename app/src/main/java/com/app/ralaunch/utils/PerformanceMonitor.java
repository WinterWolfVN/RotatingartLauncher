package com.app.ralaunch.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;

import java.io.RandomAccessFile;
import java.util.LinkedList;
import java.util.Queue;

/**
 * 性能监控工具类
 * 
 * 功能：
 * - 实时FPS监控
 * - 内存使用监控（Java堆、Native堆、总内存）
 * - GC统计
 * - 内存泄漏检测
 * - 性能数据导出
 */
public class PerformanceMonitor {
    private static final String TAG = "PerformanceMonitor";
    
    private Context mContext;
    private Handler mHandler;
    private boolean mIsMonitoring = false;
    
    // ═══════════════════════════════════════════════════════════════
    // Native方法（从C#获取游戏真实性能数据）
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * [C#调用] 更新游戏性能数据
     * C#侧定期调用此方法，Java侧通过getter读取
     */
    public static native void updateGamePerformanceNative(
        float fps, float managedMemoryMB, 
        int gen0, int gen1, int gen2);
    
    /**
     * [Java调用] 获取C#游戏真实FPS
     */
    public static native float getGameFpsNative();
    
    /**
     * [Java调用] 获取C#托管内存（MB）
     */
    public static native float getManagedMemoryNative();
    
    /**
     * [Java调用] 获取GC统计信息 [Gen0, Gen1, Gen2]
     */
    public static native int[] getGCStatsNative();
    
    // FPS监控
    private long mLastFrameTime = 0;
    private Queue<Long> mFrameTimes = new LinkedList<>();
    private static final int FPS_SAMPLE_SIZE = 60; // 60帧采样
    private float mCurrentFPS = 0;
    private Choreographer.FrameCallback mFrameCallback;
    
    // 内存监控
    private long mLastJavaHeap = 0;
    private long mLastNativeHeap = 0;
    private long mLastTotalMemory = 0;
    private int mGcCount = 0;
    private long mLastGcTime = 0;
    
    // 内存泄漏检测
    private long mPeakMemory = 0;
    private int mMemoryWarningCount = 0;
    
    // CPU监控
    private long mLastCpuTime = 0;
    private long mLastAppCpuTime = 0;
    private float mCpuUsage = 0;
    
    // GPU监控
    private float mGpuUsage = 0;
    private String mGpuPath = null;
    private boolean mGpuSupported = false;
    
    // 监听器
    private PerformanceListener mListener;
    
    public interface PerformanceListener {
        void onPerformanceUpdate(PerformanceData data);
        void onMemoryWarning(String message);
        void onMemoryLeak(String message);
    }
    
    public static class PerformanceData {
        // Java侧性能数据
        public float fps;            // UI刷新帧率（Choreographer）
        public float cpuUsage;       // CPU使用率 (0-100%)
        public float gpuUsage;       // GPU使用率 (0-100%)
        
        // C#侧游戏性能数据（真实渲染帧率）
        public float gameFps;        // C#游戏真实FPS
        public float managedMemory;  // C#托管内存 (MB)
        public int gcGen0;           // GC Gen0计数
        public int gcGen1;           // GC Gen1计数
        public int gcGen2;           // GC Gen2计数
        
        // 内存数据
        public long javaHeap;        // Java堆内存 (bytes)
        public long nativeHeap;      // Native堆内存 (bytes)
        public long totalMemory;     // 总内存 (bytes)
        public long availableMemory; // 可用内存 (bytes)
        public int gcCount;          // Java GC次数（估算）
        public long gcTime;          // 上次GC时间
        public boolean isMemoryLow;  // 内存是否不足
        
        @Override
        public String toString() {
            return String.format(
                "FPS: %.1f | CPU: %.1f%% | GPU: %.1f%% | Java: %.1fMB | Native: %.1fMB | GC: %d",
                fps,
                cpuUsage,
                gpuUsage,
                javaHeap / 1024.0 / 1024.0,
                nativeHeap / 1024.0 / 1024.0,
                gcCount
            );
        }
    }
    
    public PerformanceMonitor(Context context) {
        mContext = context.getApplicationContext();
        mHandler = new Handler(Looper.getMainLooper());
        detectGpuSupport();
    }
    
    /**
     * 检测GPU监控支持
     */
    private void detectGpuSupport() {
        // 尝试多种GPU路径
        String[] gpuPaths = {
            // Adreno GPU (Qualcomm)
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/gpubusy",
            // Mali GPU (ARM)
            "/sys/devices/platform/mali.0/utilization",
            "/sys/devices/platform/13000000.mali/utilization",
            "/sys/devices/11500000.mali/utilization",
            // 通用路径
            "/sys/kernel/gpu/gpu_busy",
            "/sys/module/pvrsrvkm/parameters/sgx_gpu_utilization"
        };
        
        for (String path : gpuPaths) {
            try {
                java.io.File file = new java.io.File(path);
                if (file.exists() && file.canRead()) {
                    mGpuPath = path;
                    mGpuSupported = true;
                    Log.i(TAG, "GPU monitoring supported, using: " + path);
                    return;
                }
            } catch (Exception e) {
                // 忽略，继续尝试下一个路径
            }
        }
        
        Log.w(TAG, "GPU monitoring not supported on this device");
    }
    
    /**
     * 开始性能监控
     * 
     * @param updateIntervalMs 更新间隔（毫秒）
     * @param listener 性能数据监听器
     */
    public void startMonitoring(int updateIntervalMs, PerformanceListener listener) {
        if (mIsMonitoring) {
            Log.w(TAG, "Performance monitoring is already running");
            return;
        }
        
        mIsMonitoring = true;
        mListener = listener;
        mLastFrameTime = System.nanoTime();
        
        // 启动Choreographer帧回调（用于FPS计算）
        mFrameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (!mIsMonitoring) return;
                
                // 记录帧时间
                recordFrameInternal(frameTimeNanos);
                
                // 继续监听下一帧
                Choreographer.getInstance().postFrameCallback(this);
            }
        };
        Choreographer.getInstance().postFrameCallback(mFrameCallback);
        
        // 启动监控循环
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!mIsMonitoring) return;
                
                updatePerformanceData();
                mHandler.postDelayed(this, updateIntervalMs);
            }
        });
        
        Log.i(TAG, "Performance monitoring started (interval: " + updateIntervalMs + "ms)");
    }
    
    /**
     * 停止性能监控
     */
    public void stopMonitoring() {
        mIsMonitoring = false;
        mHandler.removeCallbacksAndMessages(null);
        if (mFrameCallback != null) {
            Choreographer.getInstance().removeFrameCallback(mFrameCallback);
        }
        Log.i(TAG, "Performance monitoring stopped");
    }
    
    /**
     * 记录一帧（用于FPS计算）- 公共API
     */
    public void recordFrame() {
        recordFrameInternal(System.nanoTime());
    }
    
    /**
     * 内部记录帧方法
     */
    private void recordFrameInternal(long frameTimeNanos) {
        long frameTime = frameTimeNanos - mLastFrameTime;
        mLastFrameTime = frameTimeNanos;
        
        // 添加到队列
        mFrameTimes.offer(frameTime);
        if (mFrameTimes.size() > FPS_SAMPLE_SIZE) {
            mFrameTimes.poll();
        }
        
        // 计算平均FPS
        if (mFrameTimes.size() >= 10) {
            long totalTime = 0;
            for (Long time : mFrameTimes) {
                totalTime += time;
            }
            double avgFrameTime = totalTime / (double) mFrameTimes.size();
            mCurrentFPS = (float) (1_000_000_000.0 / avgFrameTime);
        }
    }
    
    /**
     * 更新性能数据
     */
    private void updatePerformanceData() {
        PerformanceData data = new PerformanceData();
        
        // Java FPS (UI刷新率，Choreographer)
        data.fps = mCurrentFPS;
        
        // C#游戏真实FPS（从native获取）
        try {
            data.gameFps = getGameFpsNative();
            data.managedMemory = getManagedMemoryNative();
            int[] gcStats = getGCStatsNative();
            if (gcStats != null && gcStats.length >= 3) {
                data.gcGen0 = gcStats[0];
                data.gcGen1 = gcStats[1];
                data.gcGen2 = gcStats[2];
            }
        } catch (UnsatisfiedLinkError e) {
            // Native方法未加载，使用默认值
            data.gameFps = 0;
            data.managedMemory = 0;
        }
        
        // CPU使用率
        data.cpuUsage = getCpuUsage();
        
        // GPU使用率
        data.gpuUsage = getGpuUsage();
        
        // Java堆内存
        Runtime runtime = Runtime.getRuntime();
        data.javaHeap = runtime.totalMemory() - runtime.freeMemory();
        
        // Native堆内存
        Debug.MemoryInfo memInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memInfo);
        data.nativeHeap = memInfo.nativePss * 1024L; // PSS in KB
        
        // 总内存
        data.totalMemory = data.javaHeap + data.nativeHeap;
        
        // 可用内存
        ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) {
            am.getMemoryInfo(mi);
            data.availableMemory = mi.availMem;
            data.isMemoryLow = mi.lowMemory;
        }
        
        // GC统计（估算）
        long currentJavaHeap = data.javaHeap;
        if (currentJavaHeap < mLastJavaHeap - 5 * 1024 * 1024) {
            // Java堆内存突然减少超过5MB，可能发生了GC
            mGcCount++;
            mLastGcTime = System.currentTimeMillis();
        }
        data.gcCount = mGcCount;
        data.gcTime = mLastGcTime;
        
        // 内存泄漏检测
        checkMemoryLeak(data);
        
        // 保存当前值
        mLastJavaHeap = currentJavaHeap;
        mLastNativeHeap = data.nativeHeap;
        mLastTotalMemory = data.totalMemory;
        
        // 通知监听器
        if (mListener != null) {
            mListener.onPerformanceUpdate(data);
        }
        
        // 内存警告
        if (data.isMemoryLow && mListener != null) {
            mListener.onMemoryWarning("System memory is low: " + 
                (data.availableMemory / 1024 / 1024) + " MB available");
        }
    }
    
    /**
     * 检测内存泄漏
     */
    private void checkMemoryLeak(PerformanceData data) {
        // 更新峰值内存
        if (data.totalMemory > mPeakMemory) {
            mPeakMemory = data.totalMemory;
        }
        
        // 如果内存持续增长且不回收，可能存在内存泄漏
        if (data.totalMemory > mPeakMemory * 0.9 && mGcCount > 10) {
            // 内存使用接近峰值，且已经发生多次GC，但内存没有明显下降
            long timeSinceLastGc = System.currentTimeMillis() - mLastGcTime;
            if (timeSinceLastGc > 30000) { // 30秒没有GC
                mMemoryWarningCount++;
                if (mMemoryWarningCount >= 3 && mListener != null) {
                    mListener.onMemoryLeak(
                        String.format("Possible memory leak detected: Memory usage %.1f MB (peak: %.1f MB), " +
                            "no GC for %d seconds",
                            data.totalMemory / 1024.0 / 1024.0,
                            mPeakMemory / 1024.0 / 1024.0,
                            timeSinceLastGc / 1000)
                    );
                    mMemoryWarningCount = 0; // 重置计数器，避免重复报告
                }
            }
        }
        
        // 如果Java堆内存持续增长超过阈值
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        if (data.javaHeap > maxMemory * 0.85) {
            // Java堆内存使用超过85%
            if (mListener != null) {
                mListener.onMemoryWarning(
                    String.format("Java heap usage critical: %.1f MB / %.1f MB (%.1f%%)",
                        data.javaHeap / 1024.0 / 1024.0,
                        maxMemory / 1024.0 / 1024.0,
                        (data.javaHeap * 100.0 / maxMemory))
                );
            }
        }
    }
    
    /**
     * 手动触发GC（仅用于调试）
     */
    public void forceGC() {
        Log.w(TAG, "Forcing garbage collection...");
        System.gc();
        System.runFinalization();
        Log.w(TAG, "GC completed");
    }
    
    /**
     * 获取当前内存统计信息
     */
    public String getMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long javaHeap = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        
        Debug.MemoryInfo memInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memInfo);
        long nativeHeap = memInfo.nativePss * 1024L;
        
        return String.format(
            "Memory Stats:\n" +
            "  Java Heap: %.1f MB / %.1f MB (%.1f%%)\n" +
            "  Native Heap: %.1f MB\n" +
            "  Total: %.1f MB\n" +
            "  GC Count: %d\n" +
            "  Peak Memory: %.1f MB",
            javaHeap / 1024.0 / 1024.0,
            maxMemory / 1024.0 / 1024.0,
            (javaHeap * 100.0 / maxMemory),
            nativeHeap / 1024.0 / 1024.0,
            (javaHeap + nativeHeap) / 1024.0 / 1024.0,
            mGcCount,
            mPeakMemory / 1024.0 / 1024.0
        );
    }
    
    /**
     * 重置统计数据
     */
    public void resetStats() {
        mFrameTimes.clear();
        mCurrentFPS = 0;
        mGcCount = 0;
        mPeakMemory = 0;
        mMemoryWarningCount = 0;
        Log.i(TAG, "Performance stats reset");
    }
    
    /**
     * 获取CPU使用率
     * @return CPU使用率 (0-100%)
     */
    private float getCpuUsage() {
        try {
            // 读取/proc/stat获取总CPU时间
            RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
            String load = reader.readLine();
            reader.close();
            
            String[] toks = load.split(" +");
            long idle = Long.parseLong(toks[4]);
            long cpu = Long.parseLong(toks[1]) + Long.parseLong(toks[2]) + Long.parseLong(toks[3])
                     + Long.parseLong(toks[5]) + Long.parseLong(toks[6]) + Long.parseLong(toks[7]);
            
            // 读取/proc/self/stat获取当前进程CPU时间
            reader = new RandomAccessFile("/proc/self/stat", "r");
            load = reader.readLine();
            reader.close();
            
            toks = load.split(" ");
            long appCpu = Long.parseLong(toks[13]) + Long.parseLong(toks[14]);
            
            // 计算CPU使用率
            if (mLastCpuTime > 0) {
                long cpuDiff = cpu - mLastCpuTime;
                long appCpuDiff = appCpu - mLastAppCpuTime;
                
                if (cpuDiff > 0) {
                    mCpuUsage = (appCpuDiff * 100.0f) / cpuDiff;
                    
                    // 限制在0-100范围内
                    if (mCpuUsage < 0) mCpuUsage = 0;
                    if (mCpuUsage > 100) mCpuUsage = 100;
                }
            }
            
            mLastCpuTime = cpu;
            mLastAppCpuTime = appCpu;
            
            return mCpuUsage;
        } catch (Exception e) {
            Log.w(TAG, "Failed to get CPU usage: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * 获取GPU使用率
     * @return GPU使用率 (0-100%)
     */
    private float getGpuUsage() {
        if (!mGpuSupported || mGpuPath == null) {
            return 0;
        }
        
        try {
            RandomAccessFile reader = new RandomAccessFile(mGpuPath, "r");
            String line = reader.readLine();
            reader.close();
            
            if (line == null || line.isEmpty()) {
                return mGpuUsage;
            }
            
            // 解析不同格式的GPU使用率
            // Adreno格式: "123 456" (busy total) 或 "45" (percentage)
            // Mali格式: "45" (percentage)
            
            line = line.trim();
            String[] parts = line.split("\\s+");
            
            if (parts.length == 2) {
                // Adreno格式: busy total
                long busy = Long.parseLong(parts[0]);
                long total = Long.parseLong(parts[1]);
                if (total > 0) {
                    mGpuUsage = (busy * 100.0f) / total;
                }
            } else if (parts.length == 1) {
                // 直接百分比格式
                try {
                    mGpuUsage = Float.parseFloat(parts[0]);
                } catch (NumberFormatException e) {
                    // 可能包含其他字符，尝试提取数字
                    String numStr = parts[0].replaceAll("[^0-9.]", "");
                    if (!numStr.isEmpty()) {
                        mGpuUsage = Float.parseFloat(numStr);
                    }
                }
            }
            
            // 限制在0-100范围内
            if (mGpuUsage < 0) mGpuUsage = 0;
            if (mGpuUsage > 100) mGpuUsage = 100;
            
            return mGpuUsage;
        } catch (Exception e) {
            // 不打印错误日志，避免刷屏
            return mGpuUsage; // 返回上次的值
        }
    }
}

