package com.app.ralaunch.crash;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Native 崩溃处理器
 * 使用 FileObserver 实时监听崩溃日志文件的创建
 */
public class NativeCrashHandler implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "NativeCrashHandler";
    private final File crashLogDir;
    private Activity currentActivity;
    private long appStartTime;
    private boolean crashDialogShown = false;
    private String lastProcessedLog = null;
    private FileObserver crashLogObserver;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public NativeCrashHandler(File crashLogDir) {
        this.crashLogDir = crashLogDir;
        // 记录应用启动时间（减去 1 分钟，确保能检测到刚才的崩溃）
        this.appStartTime = System.currentTimeMillis() - 60000;
        
        // 启动文件监听器
        startFileObserver();
    }
    
    /**
     * 启动 FileObserver 监听崩溃日志目录
     */
    private void startFileObserver() {
        if (crashLogDir == null || !crashLogDir.exists()) {
            Log.w(TAG, "Crash log directory not available for FileObserver");
            return;
        }
        
        try {
            crashLogObserver = new FileObserver(crashLogDir, FileObserver.CLOSE_WRITE) {
                @Override
                public void onEvent(int event, @Nullable String path) {
                    if (path != null && path.startsWith("native_") && path.endsWith(".log")) {
                        Log.i(TAG, "FileObserver detected new crash log: " + path);
                        
                        // 延迟 100ms，确保文件写入完成
                        mainHandler.postDelayed(() -> {
                            handleNewCrashLog(path);
                        }, 100);
                    }
                }
            };
            crashLogObserver.startWatching();
            Log.d(TAG, "FileObserver started watching: " + crashLogDir.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to start FileObserver", e);
        }
    }
    
    /**
     * 处理新的崩溃日志
     */
    private void handleNewCrashLog(String fileName) {
        // 避免重复处理同一个日志
        if (fileName.equals(lastProcessedLog)) {
            Log.d(TAG, "Crash log already processed: " + fileName);
            return;
        }
        
        File logFile = new File(crashLogDir, fileName);
        if (!logFile.exists()) {
            Log.w(TAG, "Crash log file does not exist: " + fileName);
            return;
        }
        
        Log.i(TAG, "Processing new crash log: " + fileName);
        
        // 读取并显示崩溃报告
        String logContent = readLogFile(logFile);
        showCrashReport(logContent, fileName);
        crashDialogShown = true;
        lastProcessedLog = fileName;
    }
    
    /**
     * 停止 FileObserver
     */
    private void stopFileObserver() {
        if (crashLogObserver != null) {
            try {
                crashLogObserver.stopWatching();
                Log.d(TAG, "FileObserver stopped");
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop FileObserver", e);
            }
            crashLogObserver = null;
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        currentActivity = activity;
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        currentActivity = activity;
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        currentActivity = activity;
        // 检查是否有新的 native 崩溃日志
        if (!crashDialogShown) {
            checkForNativeCrash();
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        // 不处理
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        if (currentActivity == activity) {
            currentActivity = null;
        }
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        // 不处理
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (currentActivity == activity) {
            currentActivity = null;
        }
        
        // 如果所有 Activity 都销毁了，停止 FileObserver
        // （实际上在应用退出前会调用）
    }

    /**
     * 检查是否有新的 native 崩溃日志
     */
    private void checkForNativeCrash() {
        if (crashLogDir == null || !crashLogDir.exists()) {
            return;
        }

        try {
            File[] logFiles = crashLogDir.listFiles((dir, name) -> name.startsWith("native_") && name.endsWith(".log"));
            
            if (logFiles == null || logFiles.length == 0) {
                return;
            }

            // 按修改时间排序，获取最新的日志文件
            Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified).reversed());
            File latestLog = logFiles[0];

            // 检查是否是新的崩溃日志（未处理过的，且在应用启动时间之后创建）
            String logName = latestLog.getName();
            long logTime = latestLog.lastModified();
            long currentTime = System.currentTimeMillis();
            
            // 条件：1. 不是上次已处理的日志  2. 日志时间在应用启动前后  3. 最近 2 分钟内创建
            if (!logName.equals(lastProcessedLog) && 
                logTime > appStartTime && 
                (currentTime - logTime) < 120000) {
                
                // 读取崩溃日志内容
                String logContent = readLogFile(latestLog);
                showCrashReport(logContent, logName);
                crashDialogShown = true;
                lastProcessedLog = logName;
                
                Log.i(TAG, "Native crash detected: " + logName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to check for native crash", e);
        }
    }
    
    /**
     * 显式检查崩溃
     */
    public void checkForCrashAfterGameExit() {
        if (crashLogDir == null || !crashLogDir.exists()) {
            Log.w(TAG, "Crash log directory not available");
            return;
        }

        try {
            File[] logFiles = crashLogDir.listFiles((dir, name) -> name.startsWith("native_") && name.endsWith(".log"));
            
            if (logFiles == null || logFiles.length == 0) {
                Log.d(TAG, "No crash logs found");
                return;
            }

            // 按修改时间排序，获取最新的日志文件
            Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified).reversed());
            File latestLog = logFiles[0];
            String logName = latestLog.getName();
            
            Log.d(TAG, "Checking latest crash log: " + logName + ", last processed: " + lastProcessedLog);

            // 只检查是否是新的日志（不判断时间）
            if (!logName.equals(lastProcessedLog)) {
                Log.i(TAG, "New crash detected after game exit: " + logName);
                
                // 读取崩溃日志内容
                String logContent = readLogFile(latestLog);
                showCrashReport(logContent, logName);
                crashDialogShown = true;
                lastProcessedLog = logName;
            } else {
                Log.d(TAG, "Crash already processed: " + logName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to check for crash after game exit", e);
        }
    }

    /**
     * 读取日志文件内容
     */
    private String readLogFile(File logFile) {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < 500) { // 限制读取 500 行
                content.append(line).append("\n");
                lineCount++;
            }
            if (reader.readLine() != null) {
                content.append("\n... (日志已截断，完整日志请查看文件)\n");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read log file", e);
            content.append("无法读取崩溃日志: ").append(e.getMessage());
        }
        return content.toString();
    }

    /**
     * 显示崩溃报告界面
     */
    private void showCrashReport(String logContent, String fileName) {
        if (currentActivity == null) {
            Log.w(TAG, "No current activity to show crash report");
            return;
        }

        try {
            Intent intent = new Intent(currentActivity, CrashReportActivity.class);
            
            // 提取崩溃摘要信息
            String errorDetails = extractCrashSummary(logContent, fileName);
            
            intent.putExtra(CrashReportActivity.EXTRA_ERROR_DETAILS, errorDetails);
            intent.putExtra(CrashReportActivity.EXTRA_STACK_TRACE, logContent);
            intent.putExtra(CrashReportActivity.EXTRA_EXCEPTION_CLASS, "Native Crash (SIGSEGV)");
            intent.putExtra(CrashReportActivity.EXTRA_EXCEPTION_MESSAGE, "应用发生了 native 崩溃");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            currentActivity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to show crash report", e);
        }
    }

    /**
     * 提取崩溃摘要信息
     */
    private String extractCrashSummary(String logContent, String fileName) {
        StringBuilder summary = new StringBuilder();
        summary.append("崩溃类型: Native Crash (SIGSEGV)\n");
        summary.append("崩溃日志: ").append(fileName).append("\n\n");
        
        // 尝试提取关键信息
        String[] lines = logContent.split("\n");
        boolean foundSignal = false;
        boolean foundBacktrace = false;
        int backtraceCount = 0;
        
        for (String line : lines) {
            // 提取信号信息
            if (line.contains("signal") && line.contains("SIGSEGV")) {
                summary.append(line.trim()).append("\n");
                foundSignal = true;
            }
            // 提取错误地址
            else if (line.contains("fault addr")) {
                summary.append(line.trim()).append("\n");
            }
            // 提取堆栈信息（前 5 行）
            else if (line.contains("#") && line.contains("pc ") && backtraceCount < 5) {
                if (!foundBacktrace) {
                    summary.append("\n堆栈跟踪:\n");
                    foundBacktrace = true;
                }
                summary.append(line.trim()).append("\n");
                backtraceCount++;
            }
        }
        
        if (!foundSignal) {
            summary.append("详细信息请查看完整日志\n");
        }
        
        return summary.toString();
    }

    /**
     * 重置崩溃对话框显示状态（用于测试或重新检测）
     */
    public void resetCrashDialogState() {
        crashDialogShown = false;
        lastProcessedLog = null;
        appStartTime = System.currentTimeMillis() - 60000;
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        stopFileObserver();
        mainHandler.removeCallbacksAndMessages(null);
    }
}

