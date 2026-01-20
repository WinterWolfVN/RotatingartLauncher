package com.app.ralaunch.utils;

import android.content.Context;

import com.app.ralib.utils.StreamUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * .NET 运行时管理器 - 简化版
 * 
 * <p>此类负责管理 Android 应用中的 .NET 运行时，包括：
 * <ul>
 *   <li>运行时路径管理</li>
 *   <li>受信程序集列表构建</li>
 *   <li>原生库搜索路径构建</li>
 * </ul>
 * 
 * <p>直接使用默认安装的 .NET 10 运行时
 */
public final class RuntimeManager {
    private static final String TAG = "RuntimeManager";

    /** 私有构造函数，防止实例化 */
    private RuntimeManager() {}

    /**
     * 获取 .NET 运行时根目录
     */
    public static File getDotnetRoot(Context ctx) {
        String dirName = "dotnet";
        File archSpecificDir = new File(ctx.getFilesDir(), dirName);
        if (archSpecificDir.exists()) {
            return archSpecificDir;
        }
        // 回退到默认 dotnet 目录
        return new File(ctx.getFilesDir(), "dotnet");
    }

    /**
     * 获取 .NET 运行时根目录路径字符串
     */
    public static String getDotnetPath(Context ctx) {
        return getDotnetRoot(ctx).getAbsolutePath();
    }

    /**
     * 获取共享运行时目录（Microsoft.NETCore.App）
     */
    public static File getSharedRoot(Context ctx) {
        File dotnetRoot = getDotnetRoot(ctx);
        return new File(dotnetRoot, "shared/Microsoft.NETCore.App");
    }

    /**
     * 列出所有已安装的运行时版本
     * @return 已安装版本列表，按版本号排序
     */
    public static List<String> listInstalledVersions(Context ctx) {
        File shared = getSharedRoot(ctx);
        if (!shared.exists()) {
            return Collections.emptyList();
        }
        
        File[] dirs = shared.listFiles(File::isDirectory);
        List<String> res = new ArrayList<>();
        
        if (dirs != null) {
            for (File d : dirs) {
                String name = d.getName();
                // 验证是否为有效的版本号格式
                if (name.matches("\\d+\\.\\d+\\.\\d+.*")) {
                    res.add(name);
                }
            }
        }
        
        // 按版本号排序
        Collections.sort(res, (v1, v2) -> {
            try {
                String[] parts1 = v1.split("\\.");
                String[] parts2 = v2.split("\\.");
                
                for (int i = 0; i < Math.min(parts1.length, parts2.length); i++) {
                    int num1 = Integer.parseInt(parts1[i].replaceAll("[^0-9]", ""));
                    int num2 = Integer.parseInt(parts2[i].replaceAll("[^0-9]", ""));
                    
                    if (num1 != num2) {
                        return Integer.compare(num1, num2);
                    }
                }
                
                return Integer.compare(parts1.length, parts2.length);
            } catch (Exception e) {
                return v1.compareTo(v2);
            }
        });

        return res;
    }

    /**
     * 获取当前运行时版本（返回最新安装的版本）
     */
    public static String getSelectedVersion(Context ctx) {
        List<String> vers = listInstalledVersions(ctx);
        if (!vers.isEmpty()) {
            return vers.get(vers.size() - 1);
        }
        AppLogger.error(TAG, "No runtime versions installed");
        return null;
    }
    
    /**
     * 获取版本的主版本号（例如 "10.0.0" 返回 10）
     */
    public static int getMajorVersion(String version) {
        if (version == null || version.isEmpty()) return -1;
        try {
            String[] parts = version.split("\\.");
            if (parts.length > 0) {
                return Integer.parseInt(parts[0]);
            }
        } catch (Exception e) {
        }
        return -1;
    }

    /**
     * 构建受信程序集列表（Trusted Platform Assemblies）
     */
    public static String buildTrustedAssemblies(File runtimeVerDir, File appDir) {
        StringBuilder sb = new StringBuilder();
        appendDlls(sb, runtimeVerDir);
        appendDlls(sb, appDir);
        return sb.toString();
    }

    /**
     * 构建原生库搜索路径
     */
    public static String buildNativeSearchPaths(File runtimeVerDir, File appDir) {
        StringBuilder sb = new StringBuilder();
        appendPath(sb, runtimeVerDir.getAbsolutePath());
        appendPath(sb, appDir.getAbsolutePath());
        
        // 添加 host 库目录
        File hostDir = new File(runtimeVerDir.getParentFile().getParentFile().getParentFile(), "host");
        if (hostDir.exists()) {
            appendPath(sb, hostDir.getAbsolutePath());
        }
        
        appendPath(sb, "/system/lib64");
        appendPath(sb, "/vendor/lib64");
        return sb.toString();
    }

    /**
     * 递归扫描目录并收集所有 .dll 文件的绝对路径
     */
    private static void appendDlls(StringBuilder sb, File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                appendDlls(sb, f);
            } else if (f.getName().toLowerCase().endsWith(".dll")) {
                if (sb.length() > 0) sb.append(":");
                sb.append(f.getAbsolutePath());
            }
        }
    }

    /**
     * 向路径列表中追加一个路径
     */
    private static void appendPath(StringBuilder sb, String p) {
        if (p == null || p.isEmpty()) return;
        if (sb.length() > 0) sb.append(":");
        sb.append(p);
    }
}
