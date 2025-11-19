package com.app.ralaunch.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Logcat 读取器
 *
 * 从 logcat 捕获日志并保存到文件
 * 支持过滤特定 tag 和日志级别
 */
public class LogcatReader {
    private static final String TAG = "LogcatReader";

    private static LogcatReader instance;
    private Thread readerThread;
    private Process logcatProcess;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private File logFile;
    private PrintWriter logWriter;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    // 日志回调接口
    public interface LogCallback {
        void onLogReceived(String tag, String level, String message);
    }

    private LogCallback callback;
    private Handler mainHandler;

    private LogcatReader() {
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized LogcatReader getInstance() {
        if (instance == null) {
            instance = new LogcatReader();
        }
        return instance;
    }

    /**
     * 设置日志回调
     */
    public void setCallback(LogCallback callback) {
        this.callback = callback;
    }

    /**
     * 启动 logcat 读取
     *
     * @param logDir 日志保存目录
     * @param filterTags 要过滤的 tag 数组，如 {"GameLauncher", "TModLoaderPatch", "StartupHook"}
     */
    public void start(File logDir, String[] filterTags) {
        if (running.get()) {
            Log.w(TAG, "LogcatReader already running");
            return;
        }

        try {
            // 创建日志文件
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            String fileName = "ralaunch_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".log";
            logFile = new File(logDir, fileName);
            logWriter = new PrintWriter(new FileWriter(logFile, true), true);

            Log.i(TAG, "LogcatReader started, logging to: " + logFile.getAbsolutePath());

        } catch (IOException e) {
            Log.e(TAG, "Failed to create log file", e);
            return;
        }

        running.set(true);

        readerThread = new Thread(() -> {
            try {
                // 先清除旧的 logcat 缓冲区
                Runtime.getRuntime().exec("logcat -c").waitFor();

                // 构建 logcat 命令
                StringBuilder cmd = new StringBuilder("logcat -v time");

                // 添加过滤器
                if (filterTags != null && filterTags.length > 0) {
                    // 静默所有，只显示指定 tag
                    cmd.append(" *:S");
                    for (String tag : filterTags) {
                        cmd.append(" ").append(tag).append(":V");
                    }
                }

                logcatProcess = Runtime.getRuntime().exec(cmd.toString());
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(logcatProcess.getInputStream())
                );

                String line;
                while (running.get() && (line = reader.readLine()) != null) {
                    processLogLine(line);
                }

                reader.close();

            } catch (Exception e) {
                if (running.get()) {
                    Log.e(TAG, "LogcatReader error", e);
                }
            }
        }, "LogcatReader");

        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * 启动 logcat 读取（捕获所有应用日志，不过滤）
     */
    public void start(File logDir) {
        // 不过滤 tag，捕获所有日志
        start(logDir, null);
    }

    /**
     * 处理 logcat 行
     */
    private void processLogLine(String line) {
        if (line == null || line.isEmpty()) return;

        // 写入文件
        if (logWriter != null) {
            logWriter.println(line);
            logWriter.flush();
        }

        // 解析日志行并回调
        if (callback != null) {
            try {
                // logcat -v time 格式: "MM-DD HH:MM:SS.mmm D/Tag: message"
                // 例如: "11-19 10:30:45.123 I/GameLauncher: Starting game"

                int levelStart = line.indexOf(' ', 15); // 跳过时间戳
                if (levelStart > 0) {
                    int tagStart = levelStart + 1;
                    int tagEnd = line.indexOf(':', tagStart);

                    if (tagEnd > tagStart) {
                        String levelAndTag = line.substring(tagStart, tagEnd);
                        String[] parts = levelAndTag.split("/", 2);

                        if (parts.length == 2) {
                            String level = parts[0].trim();
                            String tag = parts[1].trim();
                            String message = tagEnd + 2 < line.length() ? line.substring(tagEnd + 2) : "";

                            // 主线程回调
                            final String fTag = tag;
                            final String fLevel = level;
                            final String fMessage = message;
                            mainHandler.post(() -> callback.onLogReceived(fTag, fLevel, fMessage));
                        }
                    }
                }
            } catch (Exception e) {
                // 解析失败，忽略
            }
        }
    }

    /**
     * 停止 logcat 读取
     */
    public void stop() {
        running.set(false);

        if (logcatProcess != null) {
            logcatProcess.destroy();
            logcatProcess = null;
        }

        if (readerThread != null) {
            readerThread.interrupt();
            readerThread = null;
        }

        if (logWriter != null) {
            logWriter.flush();
            logWriter.close();
            logWriter = null;
        }

        Log.i(TAG, "LogcatReader stopped");
    }

    /**
     * 获取当前日志文件
     */
    public File getLogFile() {
        return logFile;
    }

    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }
}
