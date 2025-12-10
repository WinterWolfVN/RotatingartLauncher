package com.app.ralaunch.game;

import android.content.Context;

import com.app.ralaunch.utils.AppLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 程序集补丁工具
 * 
 * 从已解压的 MonoMod 目录加载补丁程序集，并替换游戏目录中的对应文件
 * 
 * @author RA Launcher Team
 */
public class AssemblyPatcher {
    private static final String TAG = "AssemblyPatcher";
    private static final String MONOMOD_DIR = "MonoMod";

    /**
     * 应用 MonoMod 补丁到游戏目录
     *
     * @param context Android上下文
     * @param gameDirectory 游戏目录路径
     * @return 替换的程序集数量，失败返回 -1
     */
    public static int applyMonoModPatches(Context context, String gameDirectory) {
        AppLogger.info(TAG, "开始应用 MonoMod 补丁");
        AppLogger.info(TAG, "游戏目录: " + gameDirectory);

        try {
            // 1. 从解压目录加载 MonoMod 补丁
            Map<String, byte[]> patchAssemblies = loadPatchArchive(context);

            if (patchAssemblies.isEmpty()) {
                AppLogger.warn(TAG, "未找到任何补丁程序集，请先在初始化界面安装组件");
                return 0;
            }

            AppLogger.info(TAG, "已加载 " + patchAssemblies.size() + " 个补丁程序集");

            // 2. 扫描游戏目录中的程序集
            File gameDir = new File(gameDirectory);
            List<File> gameAssemblies = findGameAssemblies(gameDir);

            AppLogger.info(TAG, "找到 " + gameAssemblies.size() + " 个游戏程序集");

            // 3. 替换对应的程序集
            int patchedCount = 0;
            for (File assemblyFile : gameAssemblies) {
                String assemblyName = assemblyFile.getName();

                if (patchAssemblies.containsKey(assemblyName)) {
                    if (replaceAssembly(assemblyFile, patchAssemblies.get(assemblyName))) {
                        AppLogger.info(TAG, "✓ 已替换: " + assemblyName);
                        patchedCount++;
                    } else {
                        AppLogger.warn(TAG, "✗ 替换失败: " + assemblyName);
                    }
                }
            }

            AppLogger.info(TAG, "补丁应用完成，共处理 " + patchedCount + " 个程序集");
            return patchedCount;

        } catch (Exception e) {
            AppLogger.error(TAG, "应用补丁失败", e);
            return -1;
        }
    }

    /**
     * 从解压后的 MonoMod 目录加载补丁程序集
     */
    private static Map<String, byte[]> loadPatchArchive(Context context) {
        Map<String, byte[]> assemblies = new HashMap<>();

        try {
            File monoModDir = new File(context.getFilesDir(), MONOMOD_DIR);

            if (!monoModDir.exists() || !monoModDir.isDirectory()) {
                AppLogger.warn(TAG, "MonoMod 目录不存在: " + monoModDir.getAbsolutePath());
                return assemblies;
            }

            List<File> dllFiles = findDllFiles(monoModDir);

            for (File dllFile : dllFiles) {
                try {
                    byte[] assemblyData = java.nio.file.Files.readAllBytes(dllFile.toPath());
                    String fileName = dllFile.getName();
                    assemblies.put(fileName, assemblyData);
                    AppLogger.debug(TAG, "加载: " + fileName + " (" + assemblyData.length + " bytes)");
                } catch (IOException e) {
                    AppLogger.warn(TAG, "无法读取: " + dllFile.getName(), e);
                }
            }

        } catch (Exception e) {
            AppLogger.error(TAG, "加载 MonoMod 补丁失败", e);
        }

        return assemblies;
    }

    /**
     * 递归查找目录中的所有 .dll 文件
     */
    private static List<File> findDllFiles(File directory) {
        List<File> dllFiles = new ArrayList<>();

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    dllFiles.addAll(findDllFiles(file));
                } else if (file.getName().endsWith(".dll")) {
                    dllFiles.add(file);
                }
            }
        }

        return dllFiles;
    }

    /**
     * 扫描游戏目录，查找所有 .dll 程序集
     */
    private static List<File> findGameAssemblies(File directory) {
        List<File> assemblies = new ArrayList<>();

        if (!directory.exists() || !directory.isDirectory()) {
            return assemblies;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return assemblies;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                assemblies.addAll(findGameAssemblies(file));
            } else if (file.getName().endsWith(".dll")) {
                assemblies.add(file);
            }
        }

        return assemblies;
    }

    /**
     * 替换程序集文件
     */
    private static boolean replaceAssembly(File targetFile, byte[] assemblyData) {
        try {
            FileOutputStream outputStream = new FileOutputStream(targetFile);
            outputStream.write(assemblyData);
            outputStream.close();
            return true;
        } catch (IOException e) {
            AppLogger.error(TAG, "替换失败: " + targetFile.getName(), e);
            return false;
        }
    }
}
