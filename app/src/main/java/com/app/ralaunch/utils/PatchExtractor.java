package com.app.ralaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 补丁提取工具
 * 负责首次启动时从 assets 解压并安装补丁和 MonoMod
 */
public class PatchExtractor {
    private static final String TAG = "PatchExtractor";
    private static final String PREFS_NAME = "patch_extractor_prefs";
    private static final String KEY_PATCHES_EXTRACTED = "patches_extracted";
    private static final String KEY_MONOMOD_EXTRACTED = "monomod_extracted";

    /**
     * 检查并提取补丁（如果需要）
     * 这个方法应该在应用启动时调用，会自动判断是否需要提取
     */
    public static void extractPatchesIfNeeded(Context context) {
        // 检查是否已经提取过
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean patchesExtracted = prefs.getBoolean(KEY_PATCHES_EXTRACTED, false);
        boolean monomodExtracted = prefs.getBoolean(KEY_MONOMOD_EXTRACTED, false);
        
        boolean needExtractPatches = !patchesExtracted;
        boolean needExtractMonoMod = !monomodExtracted;
        
        // 双重检查：验证目录是否真的存在且有文件
        if (patchesExtracted) {
            File patchesDir = new File(context.getExternalFilesDir(null), "patches");
            
            // 检查目录是否存在
            if (!patchesDir.exists() || !patchesDir.isDirectory()) {
                AppLogger.warn(TAG, "补丁标记为已提取，但目录不存在，重新提取");
                needExtractPatches = true;
            } else {
                // 检查目录是否有文件（排除元数据文件）
                File[] existingFiles = patchesDir.listFiles((dir, name) -> 
                    !name.equals("patch_metadata.json") && !name.equals(".nomedia"));
                
                if (existingFiles == null || existingFiles.length == 0) {
                    AppLogger.warn(TAG, "补丁标记为已提取，但目录为空，重新提取");
                    needExtractPatches = true;
                } else {
                    // 进一步检查：确保至少有一个补丁文件夹存在
                    boolean hasPatchFolder = false;
                    for (File file : existingFiles) {
                        if (file.isDirectory()) {
                            hasPatchFolder = true;
                            break;
                        }
                    }
                    if (!hasPatchFolder) {
                        AppLogger.warn(TAG, "补丁标记为已提取，但没有补丁文件夹，重新提取");
                        needExtractPatches = true;
                    }
                }
            }
        }
        
        if (monomodExtracted) {
            File monoModDir = new File(context.getFilesDir(), "MonoMod");
            if (!monoModDir.exists() || !monoModDir.isDirectory() || 
                monoModDir.listFiles() == null || monoModDir.listFiles().length == 0) {
                AppLogger.warn(TAG, "MonoMod 标记为已提取，但目录为空，重新提取");
                needExtractMonoMod = true;
            }
        }
        
        if (!needExtractPatches && !needExtractMonoMod) {
            AppLogger.info(TAG, "补丁和 MonoMod 已全部提取，跳过");
            return;
        }
        
        // 在后台线程执行提取
        final boolean finalNeedExtractPatches = needExtractPatches;
        final boolean finalNeedExtractMonoMod = needExtractMonoMod;
        
        new Thread(() -> {
            try {
                if (finalNeedExtractPatches) {
                    extractPatches(context);
                    prefs.edit().putBoolean(KEY_PATCHES_EXTRACTED, true).apply();
                    AppLogger.info(TAG, "补丁提取完成，已标记");
                }
                
                if (finalNeedExtractMonoMod) {
                    extractAndApplyMonoMod(context);
                    prefs.edit().putBoolean(KEY_MONOMOD_EXTRACTED, true).apply();
                    AppLogger.info(TAG, "MonoMod 提取完成，已标记");
                }
            } catch (Exception e) {
                AppLogger.error(TAG, "提取失败", e);
            }
        }).start();
    }
    
    /**
     * 从 assets/patches.zip 提取补丁到外部存储
     */
    private static void extractPatches(Context context) throws Exception {
        AppLogger.info(TAG, "开始从 assets 提取补丁...");
        
        // 获取外部存储的补丁目录
        File patchesDir = new File(context.getExternalFilesDir(null), "patches");
        
        // 如果目录已存在，先清空它（确保重新提取时不会有残留文件）
        if (patchesDir.exists()) {
            AppLogger.info(TAG, "清空现有补丁目录: " + patchesDir.getAbsolutePath());
            deleteDirectory(patchesDir);
        }
        
        // 创建新目录
        patchesDir.mkdirs();
        
        AppLogger.info(TAG, "开始解压 patches.zip 到: " + patchesDir.getAbsolutePath());
        
        // 直接从 assets 解压 zip 文件
        int fileCount = 0;
        try (InputStream is = context.getAssets().open("patches.zip");
             BufferedInputStream bis = new BufferedInputStream(is);
             ZipInputStream zis = new ZipInputStream(bis)) {
            
            ZipEntry entry;
            
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                
                // 跳过顶层 patches 目录
                if (entryName.startsWith("patches/") || entryName.startsWith("patches\\")) {
                    entryName = entryName.substring(8);
                }
                
                if (entryName.isEmpty()) {
                    zis.closeEntry();
                    continue;
                }
                
                File targetFile = new File(patchesDir, entryName);
                
                // 安全检查：防止路径遍历攻击
                String canonicalDestPath = patchesDir.getCanonicalPath();
                String canonicalEntryPath = targetFile.getCanonicalPath();
                if (!canonicalEntryPath.startsWith(canonicalDestPath + File.separator)) {
                    AppLogger.warn(TAG, "跳过不安全的路径: " + entryName);
                    zis.closeEntry();
                    continue;
                }
                
                if (entry.isDirectory()) {
                    if (!targetFile.exists()) {
                        targetFile.mkdirs();
                    }
                } else {
                    // 创建父目录
                    File parent = targetFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    
                    // 写入文件
                    try (FileOutputStream fos = new FileOutputStream(targetFile);
                         BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            bos.write(buffer, 0, bytesRead);
                        }
                    }
                    
                    fileCount++;
                    if (fileCount % 5 == 0) {
                        AppLogger.debug(TAG, "已解压 " + fileCount + " 个文件...");
                    }
                }
                
                zis.closeEntry();
            }
        }
        
        AppLogger.info(TAG, "patches.zip 解压完成，共 " + fileCount + " 个文件");
        
        // 解压完成后，安装补丁 ZIP 文件
        installPatchZips(context, patchesDir);
    }
    
    /**
     * 安装目录中的所有补丁 ZIP 文件
     */
    private static void installPatchZips(Context context, File patchesDir) {
        try {
            com.app.ralib.patch.PatchManager patchManager = 
                com.app.ralaunch.RaLaunchApplication.getPatchManager();
            
            if (patchManager == null) {
                AppLogger.warn(TAG, "PatchManager 未初始化");
                return;
            }
            
            File[] zipFiles = patchesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".zip"));
            if (zipFiles == null || zipFiles.length == 0) {
                AppLogger.info(TAG, "没有找到补丁 ZIP 文件");
                return;
            }
            
            AppLogger.info(TAG, "找到 " + zipFiles.length + " 个补丁 ZIP 文件，开始安装...");
            int installedCount = 0;
            
            for (File zipFile : zipFiles) {
                try {
                    java.nio.file.Path zipPath = zipFile.toPath();
                    boolean success = patchManager.installPatch(zipPath);
                    if (success) {
                        installedCount++;
                        AppLogger.info(TAG, "✓ 补丁安装成功: " + zipFile.getName());
                    } else {
                        AppLogger.warn(TAG, "✗ 补丁安装失败: " + zipFile.getName());
                    }
                } catch (Exception e) {
                    AppLogger.error(TAG, "安装补丁时出错: " + zipFile.getName(), e);
                }
            }
            
            AppLogger.info(TAG, "补丁安装完成，成功安装 " + installedCount + " / " + zipFiles.length + " 个补丁");
        } catch (Exception e) {
            AppLogger.error(TAG, "安装补丁失败", e);
        }
    }
    
    /**
     * 提取并应用 MonoMod 补丁到所有游戏
     */
    private static void extractAndApplyMonoMod(Context context) throws Exception {
        AppLogger.info(TAG, "========================================");
        AppLogger.info(TAG, "开始处理 MonoMod 补丁");
        AppLogger.info(TAG, "========================================");
        
        // 1. 解压 MonoMod_Patch.tar.xz 到内部存储
        File monoModDir = new File(context.getFilesDir(), "MonoMod");
        if (monoModDir.exists()) {
            // 清空旧文件
            deleteDirectory(monoModDir);
        }
        monoModDir.mkdirs();
        
        // 从 assets 复制 MonoMod_Patch.tar.xz 到缓存
        File monoModTarXz = new File(context.getCacheDir(), "MonoMod_Patch.tar.xz");
        try (InputStream is = context.getAssets().open("MonoMod_Patch.tar.xz");
             FileOutputStream fos = new FileOutputStream(monoModTarXz)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }
        
        AppLogger.info(TAG, "开始解压 MonoMod_Patch.tar.xz 到: " + monoModDir.getAbsolutePath());
        
        // 解压 tar.xz
        int fileCount = 0;
        try (FileInputStream fis = new FileInputStream(monoModTarXz);
             BufferedInputStream bis = new BufferedInputStream(fis);
             XZCompressorInputStream xzIn = new XZCompressorInputStream(bis);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(xzIn)) {
            
            TarArchiveEntry entry;
            
            while ((entry = tarIn.getNextTarEntry()) != null) {
                if (!tarIn.canReadEntryData(entry)) {
                    continue;
                }
                
                String entryName = entry.getName();
                
                // 跳过顶层目录（如果有）
                if (entryName.contains("/")) {
                    String[] parts = entryName.split("/", 2);
                    if (parts.length > 1 && (parts[0].equals("MonoMod") || parts[0].equals("MonoMod_Patch"))) {
                        entryName = parts[1];
                    }
                }
                
                if (entryName.isEmpty()) {
                    continue;
                }
                
                File targetFile = new File(monoModDir, entryName);
                
                // 安全检查：防止路径遍历攻击
                String canonicalDestPath = monoModDir.getCanonicalPath();
                String canonicalEntryPath = targetFile.getCanonicalPath();
                if (!canonicalEntryPath.startsWith(canonicalDestPath + File.separator)) {
                    AppLogger.warn(TAG, "跳过不安全的路径: " + entryName);
                    continue;
                }
                
                if (entry.isDirectory()) {
                    if (!targetFile.exists()) {
                        targetFile.mkdirs();
                    }
                } else {
                    // 创建父目录
                    File parent = targetFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    
                    // 写入文件
                    try (FileOutputStream tfos = new FileOutputStream(targetFile);
                         BufferedOutputStream bos = new BufferedOutputStream(tfos)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = tarIn.read(buffer)) != -1) {
                            bos.write(buffer, 0, bytesRead);
                        }
                    }
                    
                    fileCount++;
                    if (entryName.endsWith(".dll")) {
                        AppLogger.info(TAG, "解压程序集: " + entryName);
                    }
                }
            }
        }
        
        // 清理临时文件
        monoModTarXz.delete();
        
        AppLogger.info(TAG, "MonoMod_Patch.tar.xz 解压完成，共 " + fileCount + " 个文件");
        
        // 2. 应用 MonoMod 补丁到所有已安装的游戏
        applyMonoModToAllGames(context, monoModDir);
    }
    
    /**
     * 应用 MonoMod 补丁到所有已安装的游戏
     * 复用 AssemblyPatcher 的逻辑，避免代码重复
     */
    private static void applyMonoModToAllGames(Context context, File monoModDir) {
        try {
            com.app.ralaunch.data.GameDataManager gameDataManager = 
                com.app.ralaunch.RaLaunchApplication.getGameDataManager();
            
            if (gameDataManager == null) {
                AppLogger.warn(TAG, "GameDataManager 未初始化");
                return;
            }
            
            java.util.List<com.app.ralaunch.model.GameItem> games = gameDataManager.loadGameList();
            
            if (games.isEmpty()) {
                AppLogger.info(TAG, "没有已安装的游戏，跳过 MonoMod 应用");
                return;
            }
            
            AppLogger.info(TAG, "");
            AppLogger.info(TAG, "找到 " + games.size() + " 个已安装的游戏，开始应用 MonoMod 补丁...");
            AppLogger.info(TAG, "");
            
            int totalPatched = 0;
            int gamesPatched = 0;
            
            for (com.app.ralaunch.model.GameItem game : games) {
                String gameDir = getGameDirectory(game.getGamePath());
                if (gameDir == null) {
                    AppLogger.warn(TAG, "无法确定游戏目录: " + game.getGameName());
                    continue;
                }
                
                AppLogger.info(TAG, "处理游戏: " + game.getGameName());
                AppLogger.info(TAG, "  游戏目录: " + gameDir);
                
                // 复用 AssemblyPatcher 的逻辑（关闭冗余日志）
                int patchedCount = com.app.ralaunch.game.AssemblyPatcher.applyMonoModPatches(context, gameDir, false);
                
                if (patchedCount > 0) {
                    AppLogger.info(TAG, "  ✓ 成功替换 " + patchedCount + " 个程序集");
                    totalPatched += patchedCount;
                    gamesPatched++;
                } else if (patchedCount == 0) {
                    AppLogger.info(TAG, "  ○ 没有匹配的程序集");
                } else {
                    AppLogger.warn(TAG, "  ✗ 补丁应用失败");
                }
                AppLogger.info(TAG, "");
            }
            
            AppLogger.info(TAG, "========================================");
            AppLogger.info(TAG, "MonoMod 补丁应用完成");
            AppLogger.info(TAG, "  处理游戏: " + gamesPatched + " / " + games.size());
            AppLogger.info(TAG, "  替换程序集: " + totalPatched + " 个");
            AppLogger.info(TAG, "========================================");
            
        } catch (Exception e) {
            AppLogger.error(TAG, "应用 MonoMod 补丁失败", e);
        }
    }
    
    /**
     * 从游戏程序集路径提取游戏目录
     * 例如: /sdcard/.../games/terraria_123/Terraria.dll -> /sdcard/.../games/terraria_123
     */
    private static String getGameDirectory(String gamePath) {
        if (gamePath == null || gamePath.isEmpty()) {
            return null;
        }
        
        File gameFile = new File(gamePath);
        File parentDir = gameFile.getParentFile();
        
        if (parentDir != null && parentDir.exists()) {
            return parentDir.getAbsolutePath();
        }
        
        return null;
    }
    
    /**
     * 递归删除目录
     */
    private static void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
    
    /**
     * 重置提取状态（用于测试或重新安装）
     */
    public static void resetExtractionStatus(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .remove(KEY_PATCHES_EXTRACTED)
            .remove(KEY_MONOMOD_EXTRACTED)
            .apply();
        AppLogger.info(TAG, "补丁提取状态已重置");
    }
}

