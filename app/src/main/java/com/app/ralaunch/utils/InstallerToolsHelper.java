package com.app.ralaunch.utils;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 安装工具辅助类
 *
 * 检测 ZIP 包中的安装程序并执行安装
 */
public class InstallerToolsHelper {

    private static final String TAG = "InstallerToolsHelper";
    private static final String TOOL_DIR = "tools/InstallerTools";

    /**
     * InstallerTools 检测结果
     */
    public static class InstallerResult {
        @SerializedName("ZipPath")
        public String zipPath;

        @SerializedName("GamePath")
        public String gamePath;

        @SerializedName("HasInstallScript")
        public boolean hasInstallScript;

        @SerializedName("HasInstallerDll")
        public boolean hasInstallerDll;

        @SerializedName("InstallerDllPath")
        public String installerDllPath;

        @SerializedName("TempExtractPath")
        public String tempExtractPath;

        @SerializedName("ExtractedInstallerPath")
        public String extractedInstallerPath;

        @SerializedName("Success")
        public boolean success;

        @SerializedName("Error")
        public String error;

        @Override
        public String toString() {
            return "InstallerResult{" +
                    "zipPath='" + zipPath + '\'' +
                    ", gamePath='" + gamePath + '\'' +
                    ", hasInstallScript=" + hasInstallScript +
                    ", hasInstallerDll=" + hasInstallerDll +
                    ", extractedInstallerPath='" + extractedInstallerPath + '\'' +
                    ", success=" + success +
                    ", error='" + error + '\'' +
                    '}';
        }
    }

    /**
     * 检测 ZIP 包并准备 SMAPI 安装程序
     *
     * @param context Android 上下文
     * @param zipPath SMAPI 安装包路径
     * @param gamePath 游戏安装路径
     * @return 检测结果
     */
    public static InstallerResult detectAndPrepare(Context context, String zipPath, String gamePath) {
        try {
            // 确保工具已提取
            File toolDir = ensureToolExtracted(context);
            if (toolDir == null) {
                InstallerResult errorResult = new InstallerResult();
                errorResult.error = "无法提取 InstallerTools 工具";
                return errorResult;
            }

            File toolDll = new File(toolDir, "InstallerTools.dll");
            if (!toolDll.exists()) {
                InstallerResult errorResult = new InstallerResult();
                errorResult.error = "InstallerTools.dll 不存在: " + toolDll.getAbsolutePath();
                return errorResult;
            }

            AppLogger.info(TAG, "检测 SMAPI 安装包: " + zipPath);

            // 清除旧的 logcat 缓冲区并启动捕获
            StringBuilder output = new StringBuilder();
            Thread logcatThread = new Thread(() -> {
                try {
                    // 清除旧日志
                    Runtime.getRuntime().exec("logcat -c").waitFor();

                    // 启动 logcat 捕获 DOTNET 标签
                    Process process = Runtime.getRuntime().exec("logcat -v raw DOTNET:I *:S");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (Exception e) {
                    AppLogger.error(TAG, "Logcat 读取失败", e);
                }
            });
            logcatThread.start();

            // 等待 logcat 启动
            Thread.sleep(100);

            try {
                // 使用 NetCoreHostHelper 调用 InstallerTools
                // 注意：不需要手动清理，因为 run_app() 在每次执行后会自动重置 hostfxr
                int exitCode = NetCoreHostHelper.runAssemblyForExitCode(
                        context,
                        toolDir.getAbsolutePath(),
                        "InstallerTools.dll",
                        new String[]{zipPath, gamePath}
                );

                // 等待输出完全写入
                Thread.sleep(200);

                // 停止 logcat 捕获
                logcatThread.interrupt();

                // 读取 JSON 输出（最后一行）
                String[] lines = output.toString().split("\n");
                String jsonResult = null;

                // 从最后一行开始往前找 JSON（以 { 开头）
                for (int i = lines.length - 1; i >= 0; i--) {
                    String line = lines[i].trim();
                    if (line.startsWith("{")) {
                        jsonResult = line;
                        break;
                    }
                }

                if (jsonResult == null) {
                    InstallerResult errorResult = new InstallerResult();
                    errorResult.error = "无法从输出中解析 JSON 结果";
                    return errorResult;
                }

                // 解析 JSON 结果
                Gson gson = new Gson();
                InstallerResult result = gson.fromJson(jsonResult, InstallerResult.class);

                if (result != null && result.success) {
                    AppLogger.info(TAG, "✓ SMAPI 安装包检测成功");
                    AppLogger.info(TAG, "  安装程序路径: " + result.extractedInstallerPath);
                } else {
                    AppLogger.warn(TAG, "✗ SMAPI 安装包检测失败: " + (result != null ? result.error : "未知错误"));
                }

                return result;

            } catch (InterruptedException e) {
                throw new Exception("程序执行被中断", e);
            }

        } catch (Exception e) {
            AppLogger.error(TAG, "检测 SMAPI 安装包失败", e);
            InstallerResult errorResult = new InstallerResult();
            errorResult.error = "检测失败: " + e.getMessage();
            return errorResult;
        }
    }

    /**
     * 运行 SMAPI 安装程序
     *
     * @param context Android 上下文
     * @param installerDllPath SMAPI.Installer.dll 的路径
     * @param gamePath 游戏安装路径
     * @return 安装是否成功
     */
    public static boolean runInstaller(Context context, String installerDllPath, String gamePath) {
        try {
            File installerDll = new File(installerDllPath);
            if (!installerDll.exists()) {
                AppLogger.error(TAG, "安装程序不存在: " + installerDllPath);
                return false;
            }

            String installerDir = installerDll.getParent();
            String installerName = installerDll.getName();

            AppLogger.info(TAG, "运行 SMAPI 安装程序...");
            AppLogger.info(TAG, "  安装程序: " + installerDllPath);
            AppLogger.info(TAG, "  游戏路径: " + gamePath);

            // 使用 NetCoreHostHelper 运行安装程序
            int exitCode = NetCoreHostHelper.runAssemblyForExitCode(
                    context,
                    installerDir,
                    installerName,
                    new String[]{"--game-path", gamePath, "--install"}
            );

            if (exitCode == 0) {
                AppLogger.info(TAG, "✓ SMAPI 安装成功");
                return true;
            } else {
                AppLogger.error(TAG, "✗ SMAPI 安装失败，退出码: " + exitCode);
                return false;
            }

        } catch (Exception e) {
            AppLogger.error(TAG, "运行 SMAPI 安装程序失败", e);
            return false;
        }
    }

    /**
     * 确保工具已提取到内部存储
     */
    private static File ensureToolExtracted(Context context) {
        try {
            File internalDir = context.getFilesDir();
            File toolDir = new File(internalDir, TOOL_DIR);

            if (!toolDir.exists()) {
                toolDir.mkdirs();
            }

            // 提取 InstallerTools.dll
            File dllFile = new File(toolDir, "InstallerTools.dll");
            if (!dllFile.exists() || shouldUpdateTool(context, dllFile)) {
                extractAsset(context, "InstallerTools.dll", dllFile);
                AppLogger.info(TAG, "Extracted InstallerTools.dll");
            }

            // 提取 InstallerTools.runtimeconfig.json
            File runtimeConfigFile = new File(toolDir, "InstallerTools.runtimeconfig.json");
            if (!runtimeConfigFile.exists() || shouldUpdateTool(context, runtimeConfigFile)) {
                extractAsset(context, "InstallerTools.runtimeconfig.json", runtimeConfigFile);
                AppLogger.info(TAG, "Extracted InstallerTools.runtimeconfig.json");
            }

            return toolDir;

        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to extract InstallerTools", e);
            return null;
        }
    }

    private static boolean shouldUpdateTool(Context context, File file) {
        // 简单检查：如果文件大小为 0，则需要更新
        return file.length() == 0;
    }

    private static void extractAsset(Context context, String assetName, File destFile) throws IOException {
        try (InputStream in = context.getAssets().open(assetName);
             FileOutputStream out = new FileOutputStream(destFile)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        }
    }
}
