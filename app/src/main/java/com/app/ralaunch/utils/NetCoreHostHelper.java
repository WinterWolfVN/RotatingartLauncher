package com.app.ralaunch.utils;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * .NET Core Host 辅助类
 *
 * 提供便捷的方法来运行 .NET 程序集
 */
public class NetCoreHostHelper {

    private static final String TAG = "NetCoreHostHelper";
    private static boolean initialized = false;

    /**
     * 初始化 .NET 运行时（只需调用一次）
     *
     * @param context Android 上下文
     * @param frameworkMajor 框架主版本号（默认 10 for .NET 10）
     */
    public static synchronized void initialize(Context context, int frameworkMajor) {
        if (initialized) {
            AppLogger.debug(TAG, "已初始化，跳过");
            return;
        }

        String dotnetRoot = RuntimeManager.getDotnetPath(context);
        AppLogger.info(TAG, "初始化 .NET 运行时: DOTNET_ROOT=" + dotnetRoot + ", framework=" + frameworkMajor);

        // 这里可以调用 native 的 netcore_init
        // 或者通过设置环境变量的方式初始化
        // 当前实现通过每次运行程序集时设置环境变量

        initialized = true;
    }

    /**
     * 初始化 .NET 运行时（自动根据选择的运行时版本）
     *
     * <p>此方法会自动从 RuntimeManager 获取当前选择的运行时版本，
     * 并使用其主版本号进行初始化。如果未选择版本或获取失败，则默认使用 .NET 10。
     *
     * @param context Android 上下文
     */
    public static void initialize(Context context) {
        // 获取当前选择的运行时版本
        String selectedVersion = RuntimeManager.getSelectedVersion(context);
        int frameworkMajor = 10; // 默认 .NET 10

        if (selectedVersion != null) {
            int major = RuntimeManager.getMajorVersion(selectedVersion);
            if (major > 0) {
                frameworkMajor = major;
                AppLogger.info(TAG, "使用选择的运行时版本: " + selectedVersion + " (主版本: " + frameworkMajor + ")");
            } else {
                AppLogger.warn(TAG, "无法解析运行时版本 " + selectedVersion + "，使用默认版本 .NET 10");
            }
        } else {
            AppLogger.warn(TAG, "未选择运行时版本，使用默认版本 .NET 10");
        }

        initialize(context, frameworkMajor);
    }

    /**
     * 运行 .NET 程序集并获取标准输出
     *
     * @param context Android 上下文
     * @param appDir 程序集所在目录
     * @param assemblyName 程序集名称（如 "MyApp.dll"）
     * @param args 命令行参数
     * @return 程序集的标准输出
     * @throws Exception 运行失败时抛出异常
     */
    public static String runAssembly(Context context, String appDir, String assemblyName, String[] args) throws Exception {
        // 确保已初始化
        if (!initialized) {
            initialize(context);
        }

        String dotnetRoot = RuntimeManager.getDotnetPath(context);
        File assemblyFile = new File(appDir, assemblyName);

        if (!assemblyFile.exists()) {
            throw new Exception("程序集不存在: " + assemblyFile.getAbsolutePath());
        }

        // 构建命令（使用 dotnet 命令行工具）
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(new File(appDir));

        // 设置环境变量
        pb.environment().put("DOTNET_ROOT", dotnetRoot);
        pb.environment().put("DOTNET_ROLL_FORWARD", "LatestMajor");
        pb.environment().put("DOTNET_ROLL_FORWARD_ON_NO_CANDIDATE_FX", "2");
        pb.environment().put("DOTNET_ROLL_FORWARD_TO_PRERELEASE", "1");
        pb.environment().put("HOME", appDir);
        pb.environment().put("XDG_DATA_HOME", appDir);
        pb.environment().put("XDG_CONFIG_HOME", appDir);

        // 构建完整命令
        String[] fullCommand = new String[args.length + 2];
        fullCommand[0] = dotnetRoot + "/dotnet";  // 或使用 dotnet exec
        fullCommand[1] = assemblyFile.getAbsolutePath();
        System.arraycopy(args, 0, fullCommand, 2, args.length);

        pb.command(fullCommand);
        pb.redirectErrorStream(true);  // 合并 stderr 到 stdout

        AppLogger.debug(TAG, "运行程序集: " + String.join(" ", fullCommand));

        try {
            Process process = pb.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    AppLogger.debug(TAG, "程序集输出: " + line);
                }
            }

            // 等待进程结束
            int exitCode = process.waitFor();

            if (exitCode != 0 && exitCode != 1) {  // 0 和 1 都是合法的退出码
                AppLogger.warn(TAG, "程序集退出码: " + exitCode);
                // 对于非关键错误，仍然返回输出
            }

            String result = output.toString().trim();

            // 尝试提取最后一行（通常是 JSON 输出）
            String[] lines = result.split("\n");
            if (lines.length > 0) {
                // 返回最后一个非空行
                for (int i = lines.length - 1; i >= 0; i--) {
                    String line = lines[i].trim();
                    if (!line.isEmpty() && line.startsWith("{")) {
                        return line;  // 返回 JSON 行
                    }
                }
            }

            return result;

        } catch (Exception e) {
            AppLogger.error(TAG, "运行程序集失败", e);
            throw new Exception("运行程序集失败: " + e.getMessage(), e);
        }
    }

    /**
     * 运行 .NET 程序集（无参数版本）
     *
     * @param context Android 上下文
     * @param appDir 程序集所在目录
     * @param assemblyName 程序集名称
     * @return 程序集的标准输出
     * @throws Exception 运行失败时抛出异常
     */
    public static String runAssembly(Context context, String appDir, String assemblyName) throws Exception {
        return runAssembly(context, appDir, assemblyName, new String[0]);
    }
}
