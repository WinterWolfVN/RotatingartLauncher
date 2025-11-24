package com.app.ralaunch.manager;

import android.content.Context;
import android.content.Intent;
import com.app.ralaunch.activity.GameActivity;
import com.app.ralaunch.model.GameItem;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.utils.PatchManager;
import java.io.File;

/**
 * 游戏启动管理器
 * 负责处理游戏启动逻辑
 */
public class GameLaunchManager {
    private final Context context;
    
    public GameLaunchManager(Context context) {
        this.context = context;
    }
    
    /**
     * 启动游戏
     */
    public boolean launchGame(GameItem game) {
        // 验证程序集文件是否存在
        String assemblyPath = game.getGamePath();
        File assemblyFile = new File(assemblyPath);
        
        if (!assemblyFile.exists() || !assemblyFile.isFile()) {
            AppLogger.error("GameLaunchManager", "Assembly file not found: " + assemblyPath);
            return false;
        }
        
        AppLogger.info("GameLaunchManager", "Launching game: " + game.getGameName());
        AppLogger.info("GameLaunchManager", "Assembly path: " + assemblyPath);
        
        // 获取启用的补丁列表
        PatchManager patchManager = new PatchManager(context);
        java.util.List<com.app.ralaunch.model.PatchInfo> enabledPatches = patchManager.getEnabledPatches(game);
        
        if (!enabledPatches.isEmpty()) {
            AppLogger.info("GameLaunchManager", "Enabled patches for this game:");
            for (com.app.ralaunch.model.PatchInfo patch : enabledPatches) {
                AppLogger.info("GameLaunchManager", "  - " + patch.getPatchName());
            }
        }
        
        // 创建启动 Intent
        Intent intent = new Intent(context, GameActivity.class);
        intent.putExtra("GAME_NAME", game.getGameName());
        intent.putExtra("ASSEMBLY_PATH", assemblyPath);
        intent.putExtra("GAME_ID", game.getGamePath());
        intent.putExtra("GAME_PATH", game.getGamePath());
        
        // 传递启用的补丁ID列表
        if (!enabledPatches.isEmpty()) {
            java.util.ArrayList<String> patchIds = new java.util.ArrayList<>();
            for (com.app.ralaunch.model.PatchInfo patch : enabledPatches) {
                patchIds.add(patch.getPatchId());
            }
            intent.putStringArrayListExtra("ENABLED_PATCH_IDS", patchIds);
        }
        
        // 如果有 Bootstrapper 配置，传递相关参数
        if (game.isBootstrapperPresent() && game.getBootstrapperBasePath() != null) {
            intent.putExtra("USE_BOOTSTRAPPER", true);
            intent.putExtra("GAME_BASE_PATH", game.getGameBasePath());
            intent.putExtra("BOOTSTRAPPER_BASE_PATH", game.getBootstrapperBasePath());
        }
        
        context.startActivity(intent);
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).overridePendingTransition(
                android.R.anim.fade_in, android.R.anim.fade_out);
        }
        
        return true;
    }
    
    /**
     * 启动选中的程序集文件
     */
    public boolean launchAssembly(File assemblyFile) {
        if (assemblyFile == null || !assemblyFile.exists()) {
            AppLogger.error("GameLaunchManager", "Assembly file is null or does not exist");
            return false;
        }
        
        Intent intent = new Intent(context, GameActivity.class);
        intent.putExtra("ASSEMBLY_PATH", assemblyFile.getAbsolutePath());
        intent.putExtra("GAME_NAME", assemblyFile.getName());
        
        context.startActivity(intent);
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).overridePendingTransition(
                android.R.anim.fade_in, android.R.anim.fade_out);
        }
        
        return true;
    }
}


