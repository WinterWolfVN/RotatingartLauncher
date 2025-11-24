package com.app.ralaunch.manager;

import android.app.Activity;
import android.view.View;
import android.view.WindowManager;

import com.app.ralaunch.utils.AppLogger;

/**
 * 游戏全屏管理器
 * 
 * 统一管理游戏全屏模式，包括：
 * - 全屏模式启用/禁用
 * - 窗口焦点变化处理
 * - 输入法支持配置
 * 
 * 减少 GameActivity 的代码耦合
 */
public class GameFullscreenManager {
    private static final String TAG = "GameFullscreenManager";
    
    private Activity mActivity;
    
    public GameFullscreenManager(Activity activity) {
        mActivity = activity;
    }
    
    /**
     * 启用全屏沉浸模式
     * 隐藏状态栏和导航栏，提供完整的游戏画面
     */
    public void enableFullscreen() {
        try {
            View decorView = mActivity.getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
            
            AppLogger.info(TAG, "Fullscreen mode enabled");
        } catch (Exception e) {
            AppLogger.warn(TAG, "Failed to enable fullscreen: " + e.getMessage());
        }
    }
    
    /**
     * 配置窗口以支持输入法
     * 允许窗口与输入法交互，避免 SurfaceView 阻止 IME
     */
    public void configureIME() {
        try {
            mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            mActivity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
            AppLogger.info(TAG, "Window IME flags configured");
        } catch (Throwable t) {
            AppLogger.warn(TAG, "Failed to adjust window flags for IME: " + t.getMessage());
        }
    }
    
    /**
     * 处理窗口焦点变化
     * 当窗口获得焦点时重新应用全屏模式
     */
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            try {
                enableFullscreen();
            } catch (Exception e) {
                AppLogger.warn(TAG, "Failed to re-enable fullscreen: " + e.getMessage());
            }
        }
    }
}

